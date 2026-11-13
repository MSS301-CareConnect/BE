package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.ProviderCaregiverCardResponse;
import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Caregiver bên ProviderService (qua gateway). Không gọi trong DB transaction đang mở.
public interface CaregiverPort {
    /**
     * Caregiver được phân công cho booking của trung tâm: thuộc đúng trung tâm, VERIFIED + ACTIVE, làm được mọi dịch vụ
     * đã chọn. Trả cả caregiver_id (Provider) lẫn user_id (Identity) — user_id không bao giờ lấy từ client.
     */
    AssignedCaregiver resolveAssignable(UUID centerId, UUID caregiverId, Collection<UUID> careServiceIds);

    // BK-02 (C5.2): caregiver có lịch làm việc và không nghỉ trong MỌI buổi → không thì 409 CAREGIVER_NOT_AVAILABLE;
    // Provider lỗi → 503. Trùng với booking khác vẫn do Booking kiểm (nguồn sự thật về giờ đã đặt).
    void checkAvailable(UUID caregiverId, List<OccurrenceSlot> slots);

    // SHIFT (spec §6): lịch phải tồn tại (CAREGIVER_SCHEDULE_NOT_FOUND), thuộc đúng trung tâm + caregiver đã chọn
    // (CAREGIVER_SCHEDULE_MISMATCH) và đang SCHEDULED (CAREGIVER_SCHEDULE_NOT_AVAILABLE). Đã bị booking khác giữ hay chưa
    // do Booking kiểm.
    ProviderScheduleResponse findBookableSchedule(UUID centerId, UUID caregiverId, UUID scheduleId);

    // Tự phân công (2026-11-09): caregiver của trung tâm làm được mọi dịch vụ và rảnh MỌI buổi (lịch làm việc, ngày nghỉ,
    // booking khác — Provider hỏi lại Booking). Kết quả chỉ để xếp hạng: Booking vẫn khóa + kiểm trùng lại khi gán.
    // Provider lỗi → 503 PROVIDER_UNAVAILABLE.
    List<ProviderCaregiverCardResponse> findFreeCaregivers(UUID centerId, Collection<UUID> careServiceIds,
                                                           List<OccurrenceSlot> slots);

    // 2026-11-13: caregiver VERIFIED + ACTIVE của trung tâm làm được mọi dịch vụ, chưa xét lịch (tối đa một trang 50).
    List<ProviderCaregiverCardResponse> findQualifiedCaregivers(UUID centerId, Collection<UUID> careServiceIds);

    // Giờ bắt đầu các buổi caregiver KHÔNG nhận được theo lịch làm việc / ngày nghỉ (một lần gọi cho tối đa 60 buổi).
    Set<Instant> findUnavailableStarts(UUID caregiverId, List<OccurrenceSlot> slots);
}
