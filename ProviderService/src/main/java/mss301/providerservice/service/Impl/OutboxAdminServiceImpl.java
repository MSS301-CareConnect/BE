package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.ProviderOutbox;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderOutboxMapper;
import mss301.providerservice.repository.ProviderOutboxRepository;
import mss301.providerservice.service.OutboxAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OutboxAdminServiceImpl implements OutboxAdminService {
    static Set<String> STATUSES = Set.of("PENDING", "SENT", "FAILED", "SKIPPED");
    static int MAX_NOTE_LENGTH = 400;

    ProviderOutboxRepository providerOutboxRepository;
    ProviderOutboxMapper providerOutboxMapper;
    Clock clock;

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

    private ProviderOutbox lockFailed(UUID id) {
        ProviderOutbox outbox = providerOutboxRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.OUTBOX_NOT_FOUND));
        if (!"FAILED".equals(outbox.getStatus())) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FAILED);
        }
        return outbox;
    }
}
