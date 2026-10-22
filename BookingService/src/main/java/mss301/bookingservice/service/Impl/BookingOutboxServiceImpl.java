package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.PaymentServiceClient;
import mss301.bookingservice.client.dto.RefundCreateRequest;
import mss301.bookingservice.client.dto.RemainingInvoiceRequest;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.mapper.BookingOutboxMapper;
import mss301.bookingservice.repository.BookingOutboxRepository;
import mss301.bookingservice.service.BookingOutboxService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingOutboxServiceImpl implements BookingOutboxService {

    static int MAX_ERROR_LENGTH = 500;

    BookingOutboxRepository bookingOutboxRepository;
    BookingOutboxMapper bookingOutboxMapper;
    PaymentServiceClient paymentServiceClient;
    ObjectMapper objectMapper;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    public void enqueue(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, Object payload) {
        if (bookingOutboxRepository.existsByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey)) {
            log.info("Outbox {} already queued, skipping", idempotencyKey);
            return;
        }
        BookingOutbox outbox = bookingOutboxRepository.save(bookingOutboxMapper.toOutbox(eventType, aggregateId,
                idempotencyKey, objectMapper.writeValueAsString(payload), OutboxStatus.PENDING, 0, clock.instant()));
        log.info("Outbox {} queued for {} ({})", outbox.getId(), aggregateId, eventType);
        // Gửi ngay khi transaction nghiệp vụ đã commit (rule #24); rollback thì dòng outbox cũng không tồn tại.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            UUID outboxId = outbox.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Nghiệp vụ đã commit: lỗi gửi chỉ để lại dòng PENDING cho scheduler, không làm hỏng response.
                    try {
                        dispatch(outboxId);
                    } catch (RuntimeException e) {
                        log.warn("Immediate dispatch of outbox {} failed ({}), scheduler will retry", outboxId,
                                e.getClass().getSimpleName());
                    }
                }
            });
        }
    }

    @Override
    public List<UUID> findDueIds() {
        return bookingOutboxRepository.findDueIds(OutboxStatus.PENDING, clock.instant(),
                PageRequest.of(0, bookingProperties.getOutbox().getBatchSize()));
    }

    @Override
    public void dispatch(UUID id) {
        // REQUIRES_NEW: dispatch có thể chạy trong afterCommit của transaction nghiệp vụ (đã commit nhưng vẫn gắn thread).
        TransactionTemplate newTransaction = newTransaction();
        BookingOutbox claimed = newTransaction.execute(status -> claim(id));
        if (claimed == null) {
            return;
        }
        try {
            send(claimed);
        } catch (RuntimeException e) {
            newTransaction.executeWithoutResult(status -> recordFailure(id, e));
            return;
        }
        newTransaction.executeWithoutResult(status -> recordSuccess(id));
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate newTransaction = new TransactionTemplate(transactionTemplate.getTransactionManager());
        newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return newTransaction;
    }

    // Nhận việc: chỉ dòng PENDING đã tới hạn; dời hạn thêm một khoảng "thuê" để lượt khác không gửi trùng khi đang gửi.
    private BookingOutbox claim(UUID id) {
        BookingOutbox outbox = bookingOutboxRepository.findForUpdate(id).orElse(null);
        Instant now = clock.instant();
        if (outbox == null || outbox.getStatus() != OutboxStatus.PENDING || outbox.getNextAttemptAt().isAfter(now)) {
            return null;
        }
        outbox.setNextAttemptAt(now.plus(bookingProperties.getOutbox().getClaimLease()));
        return outbox;
    }

    private void send(BookingOutbox outbox) {
        // Bên nhận khử trùng theo key cố định của việc (gửi lại sau timeout không tạo bản ghi thứ hai).
        String idempotencyKey = outbox.getIdempotencyKey();
        switch (outbox.getEventType()) {
            case REMAINING_INVOICE -> paymentServiceClient.createRemainingInvoice(idempotencyKey,
                    objectMapper.readValue(outbox.getPayload(), RemainingInvoiceRequest.class));
            case REFUND -> paymentServiceClient.createRefund(idempotencyKey,
                    objectMapper.readValue(outbox.getPayload(), RefundCreateRequest.class));
        }
    }

    private void recordSuccess(UUID id) {
        bookingOutboxRepository.findById(id).ifPresent(outbox -> {
            outbox.setStatus(OutboxStatus.SENT);
            outbox.setSentAt(clock.instant());
            outbox.setAttempts(outbox.getAttempts() + 1);
            outbox.setLastError(null);
            log.info("Outbox {} sent ({})", id, outbox.getEventType());
        });
    }

    // Retry có giới hạn với backoff tăng dần (rule #25); quá giới hạn → FAILED để người vận hành xử lý.
    private void recordFailure(UUID id, RuntimeException error) {
        bookingOutboxRepository.findById(id).ifPresent(outbox -> {
            int attempts = outbox.getAttempts() + 1;
            outbox.setAttempts(attempts);
            outbox.setLastError(truncate(error.getClass().getSimpleName() + ": " + error.getMessage()));
            BookingProperties.Outbox settings = bookingProperties.getOutbox();
            if (attempts >= settings.getMaxAttempts()) {
                outbox.setStatus(OutboxStatus.FAILED);
                log.error("Outbox {} failed permanently after {} attempts ({})", id, attempts, outbox.getEventType());
                return;
            }
            Duration backoff = settings.getBaseBackoff().multipliedBy(1L << Math.min(attempts - 1, 10));
            Duration delay = backoff.compareTo(settings.getMaxBackoff()) > 0 ? settings.getMaxBackoff() : backoff;
            outbox.setNextAttemptAt(clock.instant().plus(delay));
            log.warn("Outbox {} attempt {} failed ({}), retry in {}", id, attempts, error.getClass().getSimpleName(), delay);
        });
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
