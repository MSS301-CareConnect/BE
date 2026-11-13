package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    // caregiver / khách; vẫn còn buổi chưa có người → báo trung tâm (lần đầu, rồi theo các mốc unassigned-remind-before).
    void autoAssign(UUID bookingId);
}
