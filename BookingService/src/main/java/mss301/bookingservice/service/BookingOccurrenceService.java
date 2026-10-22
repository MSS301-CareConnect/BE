package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BusyCaregiverResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Buổi chăm sóc (booking_occurrences) — lịch caregiver và chi tiết một buổi. Phạm vi theo role do service chọn,
 * ngoài phạm vi luôn 404 OCCURRENCE_NOT_FOUND (không lộ tồn tại).
 */
public interface BookingOccurrenceService {

    // CAREGIVER: lịch được giao trong [from, to] (ngày theo múi giờ booking, bỏ trống = không giới hạn), theo trang.
    APIResponse<PageResponse<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                                OccurrenceStatus status, int page, int size);

    // ADMIN / SERVICE: mọi buổi; CENTER_MANAGER: buổi của trung tâm mình; CUSTOMER: buổi thuộc booking của mình;
    // CAREGIVER: buổi được giao (snapshot người bệnh chỉ khi còn quyền — SEC-09).
    APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id);

    // SERVICE (ProviderService, C5.1): caregiver nào đã có buổi còn hoạt động trùng một trong các khoảng.
    APIResponse<BusyCaregiverResponse> listBusyCaregivers(BusyCaregiverRequest request);

    // CAREGIVER được giao xác nhận nhận ca: SCHEDULED → ACKNOWLEDGED, chỉ khi booking đã xác nhận và buổi chưa bắt đầu.
    APIResponse<CaregiverOccurrenceResponse> acknowledge(UUID actorId, UUID id);

    // OPS-06 — CAREGIVER được giao báo khách vắng mặt trong [start, end + window); buổi chưa đổi trạng thái.
    APIResponse<CaregiverOccurrenceResponse> reportNoShow(UUID actorId, UUID id, NoShowReportRequest request);

    // OPS-06 — CENTER_MANAGER cùng trung tâm: xác nhận → CUSTOMER_NO_SHOW; bác → MISSED + hoàn tiền khách.
    APIResponse<CaregiverOccurrenceResponse> decideNoShow(UUID actorId, UUID centerId, UUID id,
                                                          NoShowDecisionRequest request);

    // Job MISSED (OPS-06): id buổi quá hạn check-in; markMissed xử lý từng buổi trong transaction riêng.
    List<UUID> findMissedIds();

    void markMissed(UUID id);

    // Nhắc manager báo vắng chưa quyết định (một lần mỗi buổi).
    List<UUID> findUndecidedNoShowIds();

    void remindNoShowDecision(UUID id);
}
