package mss301.bookingservice.service;

import mss301.bookingservice.enums.OutboxEventType;

import java.util.List;
import java.util.UUID;

/**
 * booking_outbox — dùng chung cho mọi việc gửi sang service khác (hóa đơn REMAINING, hoàn tiền, rating…).
 * enqueue chạy TRONG transaction nghiệp vụ của caller; gửi thật xảy ra sau commit và được retry bởi scheduler.
 */
public interface BookingOutboxService {
    // idempotencyKey xác định duy nhất một việc (vd REFUND:CAREGIVER_MISSED:<occurrenceId>) — xếp lại cùng key thì bỏ qua.
    void enqueue(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, Object payload);

    List<UUID> findDueIds();

    // Gửi một dòng (mạng ngoài transaction); lỗi → tăng attempts, hẹn lần sau, quá giới hạn → FAILED. Không ném lỗi.
    void dispatch(UUID id);
}
