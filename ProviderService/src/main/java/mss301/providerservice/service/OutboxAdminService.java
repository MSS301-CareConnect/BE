package mss301.providerservice.service;

import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.dto.response.PageResponse;

import java.util.UUID;

// H-08: admin xem / gửi lại / bỏ qua sự kiện cascade trung tâm đã FAILED. Sự kiện sau của cùng trung tâm chờ tới khi
// sự kiện FAILED trước nó được gửi lại thành công hoặc bị bỏ qua (SKIPPED).
public interface OutboxAdminService {
    APIResponse<PageResponse<OutboxEventResponse>> list(String status, int page, int size);

    APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id);

    APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note);
}
