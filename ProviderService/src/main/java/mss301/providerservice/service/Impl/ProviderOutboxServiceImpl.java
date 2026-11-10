package mss301.providerservice.service.Impl;

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

    ProviderOutboxRepository providerOutboxRepository;
    ProviderOutboxMapper providerOutboxMapper;
    CenterCascadeClient centerCascadeClient;
    TransactionTemplate transactionTemplate;
    ObjectMapper objectMapper;
    Clock clock;
    ProviderProperties providerProperties;

    @Override
    public void enqueue(CareCenter center, String event, String reason) {
        CenterCascadeRequest request = providerOutboxMapper.toCascadeRequest(center, event, reason);
        save(center.getId(), event, center.getVersion(), request);
    }

    @Override
    public void enqueueCaregiverDeactivated(CaregiverProfile caregiver, String reason) {
        CenterCascadeRequest request = providerOutboxMapper.toCaregiverCascadeRequest(caregiver.getCenter().getId(),
                caregiver.getId(), CAREGIVER_DEACTIVATED, reason);
        // sequence_no chỉ cần duy nhất trong (center, event_type): mốc thời gian (ms) của lần đổi trạng thái.
        save(caregiver.getCenter().getId(), CAREGIVER_DEACTIVATED, clock.millis(), request);
    }

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

    @Override
    public List<UUID> findDueIds() {
        return providerOutboxRepository.findDueIds("PENDING", clock.instant(), PageRequest.of(0, 100));
    }

    @Override
    public void dispatch(UUID id) {
        TransactionTemplate write = new TransactionTemplate(transactionTemplate.getTransactionManager());
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        ProviderOutbox claimed = write.execute(status -> {
            ProviderOutbox outbox = providerOutboxRepository.findForUpdate(id).orElse(null);
            if (outbox == null || !"PENDING".equals(outbox.getStatus()) || outbox.getNextAttemptAt().isAfter(clock.instant())
                    || (!CAREGIVER_DEACTIVATED.equals(outbox.getEventType())
                        && providerOutboxRepository.existsBlockingPredecessor(outbox.getCenterId(), outbox.getSequenceNo()))) {
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
}
