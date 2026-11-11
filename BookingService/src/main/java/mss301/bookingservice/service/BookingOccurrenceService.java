package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.request.OccurrenceAcknowledgementRequest;
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

    // CAREGIVER: lịch được giao trong [from, to] (ngày theo múi giờ booking, bỏ trống = không giới hạn), theo trang;
    // bookingId (tùy chọn) = chỉ các buổi của một booking (2026-11-09).
    APIResponse<PageResponse<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                                OccurrenceStatus status, UUID bookingId, int page,
                                                                int size);

    // ADMIN / SERVICE: mọi buổi; CENTER_MANAGER: buổi của trung tâm mình; CUSTOMER: buổi thuộc booking của mình;
    // CAREGIVER: buổi được giao (snapshot người bệnh chỉ khi còn quyền — SEC-09).
    APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id);

    // SERVICE (ProviderService, C5.1): caregiver nào đã có buổi còn hoạt động trùng một trong các khoảng.
    APIResponse<BusyCaregiverResponse> listBusyCaregivers(BusyCaregiverRequest request);

    // CAREGIVER được giao xác nhận nhận ca: SCHEDULED → ACKNOWLEDGED, chỉ khi booking đã xác nhận và buổi chưa bắt đầu.
    APIResponse<CaregiverOccurrenceResponse> acknowledge(UUID actorId, UUID id);

    // 2026-11-10 — CAREGIVER nhận nhanh nhiều buổi SCHEDULED của cùng một booking; một buổi sai thì cả lệnh bị từ chối.
    APIResponse<List<CaregiverOccurrenceResponse>> acknowledgeMany(UUID actorId,
                                                                   OccurrenceAcknowledgementRequest request);

    // OPS-06 — CAREGIVER được giao báo khách vắng mặt trong [start, end + window); buổi chưa đổi trạng thái.
    APIResponse<CaregiverOccurrenceResponse> reportNoShow(UUID actorId, UUID id, NoShowReportRequest request);

    // OPS-06 — CENTER_MANAGER cùng trung tâm: xác nhận → CUSTOMER_NO_SHOW; bác → MISSED + hoàn tiền khách.
    APIResponse<CaregiverOccurrenceResponse> decideNoShow(UUID actorId, UUID centerId, UUID id,
                                                          NoShowDecisionRequest request);

    // Job MISSED (OPS-06): id buổi quá hạn check-in; markMissed xử lý từng buổi trong transaction riêng.
    List<UUID> findMissedIds();

    void markMissed(UUID id);

    // 2026-11-10 — cảnh báo sớm: buổi đã tới giờ mà caregiver chưa check-in (còn trong hạn check-in trễ) → báo caregiver,
    // trung tâm và khách một lần, trước khi job MISSED ghi nhận vắng ca.
    List<UUID> findLateCheckInIds();

    void warnLateCheckIn(UUID id);

    List<UUID> findShiftReminderIds();

    void remindShift(UUID id);

    // Nhắc manager báo vắng chưa quyết định (một lần mỗi buổi).
    List<UUID> findUndecidedNoShowIds();

    // D21 (H-10): báo vắng không được quyết sau giờ kết thúc + 24h → hệ thống tự chấp nhận, báo khách + trung tâm.
    void autoAcceptNoShow(UUID id);

    // Module 2 (SPEC V3): Trung tâm bố trí người thay thế khẩn cấp
    APIResponse<Void> emergencyReassign(UUID actorId, UUID centerId, UUID bookingId,
                                        mss301.bookingservice.dto.request.EmergencyReassignmentRequest request);

    // Module 2 (SPEC V3): Khách phản hồi người thay thế khẩn cấp (ACCEPT, RESCHEDULE, REJECT_AND_CANCEL)
    APIResponse<Void> consentReplacement(UUID actorId, UUID occurrenceId,
                                         mss301.bookingservice.dto.request.ReplacementConsentRequest request);

    // Module 3 (SPEC V3): Xem trước dời lịch buổi chăm sóc
    APIResponse<mss301.bookingservice.dto.response.OccurrenceRescheduleResponse> reschedulePreview(
            UUID actorId, Set<RoleCode> roles, UUID centerId, UUID occurrenceId,
            mss301.bookingservice.dto.request.OccurrenceReschedulePreviewRequest request);

    // Module 3 (SPEC V3): Thực hiện dời lịch buổi chăm sóc
    APIResponse<mss301.bookingservice.dto.response.OccurrenceRescheduleResponse> reschedule(
            UUID actorId, Set<RoleCode> roles, UUID centerId, UUID occurrenceId,
            mss301.bookingservice.dto.request.OccurrenceRescheduleRequest request);
}
