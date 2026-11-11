package mss301.providerservice.service;

import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import java.util.List;
import java.util.UUID;

public interface ProviderOutboxService {
    // Xếp sự kiện cascade của trung tâm.
    void enqueue(CareCenter center, String event, String reason);

    // H-11: caregiver ACTIVE → INACTIVE / SUSPENDED / ARCHIVED. Không xếp hàng theo sự kiện trung tâm.
    void enqueueCaregiverDeactivated(CaregiverProfile caregiver, String reason);
    // Lấy id sự kiện tới hạn gửi.
    List<UUID> findDueIds();
    // Gửi một sự kiện outbox.
    void dispatch(UUID id);

    // H-08: admin xem / gửi lại / bỏ qua sự kiện cascade trung tâm đã FAILED. Sự kiện sau của cùng trung tâm chờ tới khi
    // sự kiện FAILED trước nó được gửi lại thành công hoặc bị bỏ qua (SKIPPED).
    APIResponse<PageResponse<OutboxEventResponse>> list(String status, int page, int size);

    // Xếp lại sự kiện FAILED.
    APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id);

    // Bỏ qua sự kiện FAILED.
    APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note);
}
