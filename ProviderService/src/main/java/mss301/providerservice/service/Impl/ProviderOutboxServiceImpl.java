package mss301.providerservice.service.Impl;

import java.util.Set;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.entity.CaregiverProfile;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.config.ProviderProperties;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.entity.ProviderOutbox;
import mss301.providerservice.client.CenterCascadeClient;
import mss301.providerservice.client.dto.CenterCascadeRequest;
import mss301.providerservice.mapper.ProviderOutboxMapper;
import mss301.providerservice.repository.ProviderOutboxRepository;
import mss301.providerservice.service.ProviderOutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProviderOutboxServiceImpl implements ProviderOutboxService {
    static final String CAREGIVER_DEACTIVATED = "CAREGIVER_DEACTIVATED";
    // Sự kiện khóa / mở trung tâm phải đi theo thứ tự; SENT / SKIPPED là đã xong, không còn chặn sự kiện sau.
    static final List<String> ORDERED_EVENT_TYPES = List.of("SUSPEND", "RESUME");
    static final List<String> FINISHED_STATUSES = List.of("SENT", "SKIPPED");
    static Set<String> STATUSES = Set.of("PENDING", "SENT", "FAILED", "SKIPPED");
    static int MAX_NOTE_LENGTH = 400;

    ProviderOutboxRepository providerOutboxRepository;
    ProviderOutboxMapper providerOutboxMapper;
    CenterCascadeClient centerCascadeClient;
    TransactionTemplate transactionTemplate;
    ObjectMapper objectMapper;
    Clock clock;
    ProviderProperties providerProperties;

    // Xếp sự kiện cascade khi trung tâm bị khóa / mở.
    @Override
    public void enqueue(CareCenter center, String event, String reason) {
        CenterCascadeRequest request = providerOutboxMapper.toCascadeRequest(center, event, reason);
        save(center.getId(), event, center.getVersion(), request);
    }

    // Xếp sự kiện cascade khi caregiver ngừng hoạt động.
    @Override
    public void enqueueCaregiverDeactivated(CaregiverProfile caregiver, String reason) {
        CenterCascadeRequest request = providerOutboxMapper.toCaregiverCascadeRequest(caregiver.getCenter().getId(),
                caregiver.getId(), CAREGIVER_DEACTIVATED, reason);
        // sequence_no chỉ cần duy nhất trong (center, event_type): mốc thời gian (ms) của lần đổi trạng thái.
        save(caregiver.getCenter().getId(), CAREGIVER_DEACTIVATED, clock.millis(), request);
    }

    // Danh sách sự kiện outbox cho admin, lọc theo trạng thái.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<OutboxEventResponse>> list(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        if (status != null && !STATUSES.contains(status)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ProviderOutbox> rows = status == null
                ? providerOutboxRepository.findByDeletedAtIsNull(pageRequest)
                : providerOutboxRepository.findByStatusAndDeletedAtIsNull(status, pageRequest);
        PageResponse<OutboxEventResponse> response = PageResponse.of(rows,
                rows.getContent().stream().map(providerOutboxMapper::toResponse).toList());
        return APIResponse.success("Lấy danh sách outbox thành công", response);
    }

    // Đưa sự kiện FAILED về PENDING để gửi lại.
    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id) {
        ProviderOutbox outbox = lockFailed(id);
        outbox.setStatus("PENDING");
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(clock.instant());
        providerOutboxRepository.flush();
        log.warn("Admin {} requeued provider outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã xếp lại sự kiện outbox", providerOutboxMapper.toResponse(outbox));
    }

    // Bỏ qua sự kiện FAILED đã xử lý tay (SKIPPED).
    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note) {
        ProviderOutbox outbox = lockFailed(id);
        outbox.setStatus("SKIPPED");
        String detail = "Admin xử lý tay: " + note.trim();
        outbox.setLastError(detail.length() <= MAX_NOTE_LENGTH ? detail : detail.substring(0, MAX_NOTE_LENGTH));
        providerOutboxRepository.flush();
        log.warn("Admin {} skipped provider outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã bỏ qua sự kiện outbox", providerOutboxMapper.toResponse(outbox));
    }

    // Lưu sự kiện outbox và đăng ký gửi ngay sau khi transaction commit.
    private void save(UUID centerId, String event, Long sequenceNo, CenterCascadeRequest request) {
        ProviderOutbox outbox = providerOutboxRepository.saveAndFlush(providerOutboxMapper.toProviderOutbox(centerId,
                event, sequenceNo, objectMapper.writeValueAsString(request), "PENDING", 0, clock.instant()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatch(outbox.getId());
                } catch (RuntimeException exception) {
                    log.error("Gửi ngay provider outbox {} thất bại, scheduler sẽ retry", outbox.getId(), exception);
                }
            }
        });
    }

    // Lấy id sự kiện outbox đang chờ và tới hạn gửi.
    @Override
    public List<UUID> findDueIds() {
        return providerOutboxRepository.findDueIds("PENDING", clock.instant(), PageRequest.of(0, 100));
    }

    // Gửi một sự kiện outbox: nhận dòng, gọi BookingService ngoài transaction, ghi kết quả.
    @Override
    public void dispatch(UUID id) {
        TransactionTemplate write = new TransactionTemplate(transactionTemplate.getTransactionManager());
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ProviderOutbox claimed = write.execute(status -> {
            ProviderOutbox outbox = providerOutboxRepository.findForUpdate(id).orElse(null);
            if (outbox == null || !"PENDING".equals(outbox.getStatus()) || outbox.getNextAttemptAt().isAfter(clock.instant())
                    || (!CAREGIVER_DEACTIVATED.equals(outbox.getEventType())
                        && providerOutboxRepository.existsByCenterIdAndSequenceNoLessThanAndEventTypeInAndStatusNotInAndDeletedAtIsNull(outbox.getCenterId(), outbox.getSequenceNo(),
                                ORDERED_EVENT_TYPES, FINISHED_STATUSES))) {
                return null;
            }
            outbox.setNextAttemptAt(clock.instant().plus(providerProperties.getOutbox().getLease()));
            return outbox;
        });
        if (claimed == null) {
            return;
        }
        try {
            send(claimed);
        } catch (RuntimeException exception) {
            write.executeWithoutResult(status -> providerOutboxRepository.findForUpdate(id).ifPresent(outbox -> {
                int attempts = outbox.getAttempts() + 1;
                outbox.setAttempts(attempts);
                outbox.setStatus(isPermanentFailure(exception) || attempts >= providerProperties.getOutbox().getMaxAttempts() ? "FAILED" : "PENDING");
                outbox.setLastError(exception.getClass().getSimpleName());
                outbox.setNextAttemptAt(clock.instant().plusSeconds(Math.min(
                        providerProperties.getOutbox().getMaxBackoff().toSeconds(), 5L << Math.min(attempts, 9))));
            }));
            log.error("Provider outbox {} gửi thất bại", id, exception);
            return;
        }
        write.executeWithoutResult(status -> providerOutboxRepository.findForUpdate(id).ifPresent(outbox -> {
            outbox.setStatus("SENT");
            outbox.setAttempts(outbox.getAttempts() + 1);
            outbox.setLastError(null);
        }));
    }

    // Bên nhận từ chối hẳn lệnh thì không thử lại.
    private boolean isPermanentFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof AppException appException
                    && appException.getErrorCode() == ErrorCode.REMOTE_COMMAND_REJECTED) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // Gọi BookingService theo loại sự kiện: hủy booking, báo manager.
    private void send(ProviderOutbox outbox) {
        CenterCascadeRequest request = objectMapper.readValue(outbox.getPayload(), CenterCascadeRequest.class);
        switch (request.getEvent()) {
            case "SUSPEND" -> {
                centerCascadeClient.updateAccounts(request.getCenterId(),
                        providerOutboxMapper.toAccountsStatusRequest("LOCKED", request.getReason()));
                centerCascadeClient.cancelBookings(providerOutboxMapper.toCancellationRequest(request.getCenterId(),
                        "CENTER_LOCKED", request.getReason()));
            }
            case "RESUME" -> centerCascadeClient.updateAccounts(request.getCenterId(),
                    providerOutboxMapper.toAccountsStatusRequest("ACTIVE", request.getReason()));
            case CAREGIVER_DEACTIVATED -> centerCascadeClient.cancelBookings(
                    providerOutboxMapper.toCaregiverCancellationRequest(request.getCaregiverId(), CAREGIVER_DEACTIVATED,
                            request.getReason()));
            case "ACTIVATE" -> {
                if (request.getManagerUserId() != null) {
                    centerCascadeClient.notifyManager(providerOutboxMapper.toNotificationRequest(List.of(request.getManagerUserId()),
                            "CENTER_ACTIVATED", "Trung tâm đã được kích hoạt", "Trung tâm của bạn đã được duyệt hoạt động",
                            "CARE_CENTER", request.getCenterId()));
                }
            }
            default -> throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    // Khóa sự kiện outbox đang FAILED, không phải FAILED thì báo lỗi.
    private ProviderOutbox lockFailed(UUID id) {
        ProviderOutbox outbox = providerOutboxRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.OUTBOX_NOT_FOUND));
        if (!"FAILED".equals(outbox.getStatus())) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FAILED);
        }
        return outbox;
    }
}
