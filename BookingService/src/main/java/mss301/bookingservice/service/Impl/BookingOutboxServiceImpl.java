package mss301.bookingservice.service.Impl;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.service.BookingNotificationService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.PaymentServiceClient;
import mss301.bookingservice.client.dto.RefundCreateRequest;
import mss301.bookingservice.client.dto.RemainingInvoiceRequest;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
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
import mss301.bookingservice.client.dto.ComplaintRefundRequest;
import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.repository.FeedbackRepository;
import mss301.bookingservice.mapper.FeedbackMapper;
import mss301.bookingservice.enums.FeedbackVisibility;
import mss301.bookingservice.repository.projection.FeedbackRatingView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingOutboxServiceImpl implements BookingOutboxService {

    static int MAX_ERROR_LENGTH = 500;
    static int MAX_NOTE_LENGTH = 400;

    BookingOutboxRepository bookingOutboxRepository;
    BookingOutboxMapper bookingOutboxMapper;
    PaymentServiceClient paymentServiceClient;
    ObjectMapper objectMapper;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;
    ProviderServiceClient providerServiceClient;
    FeedbackRepository feedbackRepository;
    FeedbackMapper feedbackMapper;
    BookingNotificationService bookingNotificationService;

    // Xếp việc gửi sang service khác (bỏ qua nếu trùng key) và gửi ngay sau commit.
    @Override
    public void enqueue(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, Object payload) {
        enqueue(eventType, aggregateId, idempotencyKey, payload, clock.instant());
    }

    // Xếp việc gửi từ notBefore; tới giờ rồi thì gửi ngay sau commit như enqueue thường.
    @Override
    public void enqueue(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, Object payload,
                        Instant notBefore) {
        if (bookingOutboxRepository.existsByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey)) {
            log.info("Outbox {} already queued, skipping", idempotencyKey);
            return;
        }
        Instant now = clock.instant();
        Instant nextAttemptAt = notBefore.isAfter(now) ? notBefore : now;
        BookingOutbox outbox = bookingOutboxRepository.save(bookingOutboxMapper.toOutbox(eventType, aggregateId,
                idempotencyKey, objectMapper.writeValueAsString(payload), OutboxStatus.PENDING, 0, nextAttemptAt));
        log.info("Outbox {} queued for {} ({}) from {}", outbox.getId(), aggregateId, eventType, nextAttemptAt);
        // Gửi ngay khi transaction nghiệp vụ đã commit (rule #24); rollback thì dòng outbox cũng không tồn tại.
        if (!nextAttemptAt.isAfter(now) && TransactionSynchronizationManager.isSynchronizationActive()) {
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

    // Lấy id việc outbox đang chờ và tới hạn gửi.
    @Override
    public List<UUID> findDueIds() {
        return bookingOutboxRepository.findDueIds(OutboxStatus.PENDING, clock.instant(),
                PageRequest.of(0, bookingProperties.getOutbox().getBatchSize()));
    }

    // Gửi một việc outbox: nhận dòng, gọi service đích ngoài transaction, ghi kết quả.
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

    // Giờ gửi của việc còn chờ theo key.
    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> findPendingAt(String idempotencyKey) {
        return bookingOutboxRepository.findByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey)
                .filter(outbox -> outbox.getStatus() == OutboxStatus.PENDING)
                .map(BookingOutbox::getNextAttemptAt);
    }

    // Gọi trong transaction nghiệp vụ. Còn trong khoảng "thuê" của lượt nhận thì coi như đang gửi: lượt nhận dời
    // next_attempt_at về now + lease nên một dòng chỉ còn hủy được khi giờ gửi xa hơn mốc đó.
    @Override
    public boolean cancelScheduled(String idempotencyKey, String note) {
        Instant now = clock.instant();
        BookingOutbox outbox = bookingOutboxRepository.findByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey)
                .flatMap(found -> bookingOutboxRepository.findForUpdate(found.getId()))
                .orElse(null);
        if (outbox == null || outbox.getStatus() != OutboxStatus.PENDING || outbox.getAttempts() > 0
                || !outbox.getNextAttemptAt().isAfter(now.plus(bookingProperties.getOutbox().getClaimLease()))) {
            return false;
        }
        outbox.setStatus(OutboxStatus.SKIPPED);
        outbox.setLastError(truncate(note));
        outbox.setIdempotencyKey(idempotencyKey + ":CANCELLED:" + outbox.getId());
        log.info("Outbox {} ({}) cancelled before sending", outbox.getId(), idempotencyKey);
        return true;
    }

    // Danh sách việc outbox cho admin, lọc theo trạng thái.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<OutboxEventResponse>> list(OutboxStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<BookingOutbox> rows = status == null
                ? bookingOutboxRepository.findByDeletedAtIsNull(pageRequest)
                : bookingOutboxRepository.findByStatusAndDeletedAtIsNull(status, pageRequest);
        PageResponse<OutboxEventResponse> response = PageResponse.of(rows,
                rows.getContent().stream().map(bookingOutboxMapper::toResponse).toList());
        return APIResponse.success("Lấy danh sách outbox thành công", response);
    }

    // Đưa việc FAILED về PENDING để gửi lại.
    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id) {
        BookingOutbox outbox = lockFailed(id);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(clock.instant());
        bookingOutboxRepository.flush();
        log.warn("Admin {} requeued outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã xếp lại việc outbox", bookingOutboxMapper.toResponse(outbox));
    }

    // Bỏ qua việc FAILED đã xử lý tay (SKIPPED).
    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note) {
        BookingOutbox outbox = lockFailed(id);
        outbox.setStatus(OutboxStatus.SKIPPED);
        String detail = "Admin xử lý tay: " + note.trim();
        outbox.setLastError(detail.length() <= MAX_NOTE_LENGTH ? detail : detail.substring(0, MAX_NOTE_LENGTH));
        bookingOutboxRepository.flush();
        log.warn("Admin {} skipped outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã bỏ qua việc outbox", bookingOutboxMapper.toResponse(outbox));
    }

    // Tạo TransactionTemplate chạy transaction mới độc lập.
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

    // Gọi service đích theo loại việc: hoàn tiền, hóa đơn, cập nhật điểm đánh giá…
    private void send(BookingOutbox outbox) {
        // Bên nhận khử trùng theo key cố định của việc (gửi lại sau timeout không tạo bản ghi thứ hai).
        String idempotencyKey = outbox.getIdempotencyKey();
        switch (outbox.getEventType()) {
            case COMPLAINT_REFUND -> paymentServiceClient.createComplaintRefund(outbox.getIdempotencyKey(),
                    objectMapper.readValue(outbox.getPayload(), ComplaintRefundRequest.class));
            case RATING -> {
                UUID caregiverId = objectMapper.readValue(outbox.getPayload(), UUID.class);
                FeedbackRatingView summary = feedbackRepository.summarizeRating(caregiverId, FeedbackVisibility.VISIBLE);
                BigDecimal average = summary.getRatingAverage() == null ? BigDecimal.ZERO
                        : BigDecimal.valueOf(summary.getRatingAverage()).setScale(2, RoundingMode.HALF_UP);
                providerServiceClient.updateRating(caregiverId, feedbackMapper.toRatingRequest(average,
                        Math.toIntExact(summary.getRatingCount())));
            }
            case REMAINING_INVOICE -> paymentServiceClient.createRemainingInvoice(idempotencyKey,
                    objectMapper.readValue(outbox.getPayload(), RemainingInvoiceRequest.class));
            case REFUND -> paymentServiceClient.createRefund(idempotencyKey,
                    objectMapper.readValue(outbox.getPayload(), RefundCreateRequest.class));
        }
    }

    // Đánh dấu việc outbox đã gửi thành công.
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
            if (isPermanentFailure(error) || attempts >= settings.getMaxAttempts()) {
                outbox.setStatus(OutboxStatus.FAILED);
                log.error("Outbox {} failed permanently after {} attempts ({})", id, attempts, outbox.getEventType());
                // H-08: admin xem / xếp lại ở /api/v1/booking-outbox.
                bookingNotificationService.notifyAdmins(NotificationType.OUTBOX_FAILED, "BOOKING_OUTBOX", id);
                return;
            }
            Duration backoff = settings.getBaseBackoff().multipliedBy(1L << Math.min(attempts - 1, 10));
            Duration delay = backoff.compareTo(settings.getMaxBackoff()) > 0 ? settings.getMaxBackoff() : backoff;
            outbox.setNextAttemptAt(clock.instant().plus(delay));
            log.warn("Outbox {} attempt {} failed ({}), retry in {}", id, attempts, error.getClass().getSimpleName(), delay);
        });
    }

    // Cắt ngắn thông báo lỗi trước khi lưu.
    private String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    // Khóa dòng (SKIP LOCKED): dòng đang được scheduler gửi thì báo xung đột, admin thử lại sau.
    private BookingOutbox lockFailed(UUID id) {
        if (!bookingOutboxRepository.existsById(id)) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FOUND);
        }
        BookingOutbox outbox = bookingOutboxRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONCURRENT_UPDATE));
        if (outbox.getStatus() != OutboxStatus.FAILED) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FAILED);
        }
        return outbox;
    }
}
