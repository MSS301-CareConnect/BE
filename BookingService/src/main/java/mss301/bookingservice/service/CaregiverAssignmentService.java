package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Tự phân công caregiver (2026-11-09, yêu cầu B). Ứng viên: cùng trung tâm, VERIFIED + ACTIVE, làm được mọi dịch vụ, rảnh
 * mọi buổi (ProviderService tìm, Booking khóa + kiểm trùng lại khi gán). Xếp hạng: ít phút đã nhận quanh lịch → đánh giá
 * cao → nhiều lượt đánh giá → nhiều kinh nghiệm. Ưu tiên một người cho cả booking, không được thì từng buổi. Center Manager
 * vẫn phân / đổi tay bằng API sẵn có và luôn ghi đè được — tự phân công chỉ chạm buổi chưa có người (PENDING_ASSIGNMENT)
 * hoặc đang chờ thay người (needsReassignment).
 */
public interface CaregiverAssignmentService {

    // Gọi NGOÀI transaction (Provider + Identity). Ứng viên đã xếp hạng và tra hồ sơ (tối đa auto-assign-candidates),
    // bỏ excludedCaregiverIds. Provider lỗi → ném 503 PROVIDER_UNAVAILABLE.
    List<AssignedCaregiver> rankCandidates(UUID centerId, Collection<UUID> careServiceIds, List<OccurrenceSlot> slots,
                                           Set<UUID> excludedCaregiverIds);

    // Gọi TRONG transaction ghi: khóa lần lượt từng ứng viên (theo thứ tự xếp hạng) và trả người đầu tiên còn rảnh mọi slot.
    Optional<AssignedCaregiver> pickFree(List<AssignedCaregiver> candidates, List<OccurrenceSlot> slots,
                                         UUID excludeBookingId);

    // Scheduler: booking đã cọc có buổi cần người, chưa thử trong auto-assign-retry.
    List<UUID> findAutoAssignIds();

    // Không @Transactional: tìm ứng viên ngoài transaction, gán trong transaction ngắn (khóa booking → caregiver), báo
    // caregiver / khách; vẫn còn buổi chưa có người → báo trung tâm (tối đa một lần / unassigned-notify-every).
    void autoAssign(UUID bookingId);
}
