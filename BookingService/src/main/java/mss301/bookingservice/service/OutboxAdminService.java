package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.OutboxStatus;

import java.util.UUID;

// H-08: admin xem / gửi lại / bỏ qua việc outbox đã FAILED (hết lượt retry hoặc bên nhận từ chối hẳn).
public interface OutboxAdminService {
    APIResponse<PageResponse<OutboxEventResponse>> list(OutboxStatus status, int page, int size);

    // FAILED → PENDING, đếm lại số lần thử, scheduler gửi ở lượt quét kế tiếp.
    APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id);

    // FAILED → SKIPPED: việc đã được xử lý tay, không gửi nữa.
    APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note);
}
