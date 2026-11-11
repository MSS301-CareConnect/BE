package mss301.bookingservice.service;

import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.enums.OutboxEventType;

import java.util.List;
import java.util.UUID;

public interface BookingOutboxService {
    // idempotencyKey xác định duy nhất một việc (vd REFUND:CAREGIVER_MISSED:<occurrenceId>) — xếp lại cùng key thì bỏ qua.
    void enqueue(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, Object payload);

    // Lấy id việc outbox tới hạn gửi.
    List<UUID> findDueIds();

    // Gửi một dòng (mạng ngoài transaction); lỗi → tăng attempts, hẹn lần sau, quá giới hạn → FAILED. Không ném lỗi.
    void dispatch(UUID id);

    // H-08: admin xem / gửi lại / bỏ qua việc outbox đã FAILED (hết lượt retry hoặc bên nhận từ chối hẳn).
    APIResponse<PageResponse<OutboxEventResponse>> list(OutboxStatus status, int page, int size);

    // FAILED → PENDING, đếm lại số lần thử, scheduler gửi ở lượt quét kế tiếp.
    APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id);

    // FAILED → SKIPPED: việc đã được xử lý tay, không gửi nữa.
    APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note);
}
