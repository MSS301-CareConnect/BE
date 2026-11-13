package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.OccurrenceRescheduleResponse;
import mss301.bookingservice.dto.request.OccurrenceRescheduleRequest;
import mss301.bookingservice.dto.request.OccurrenceReschedulePreviewRequest;
import mss301.bookingservice.dto.request.EmergencyReassignmentRequest;
import mss301.bookingservice.dto.request.ReplacementConsentRequest;
import mss301.bookingservice.dto.request.BusyCaregiverRequest;
import mss301.bookingservice.dto.request.CustomerVisitReportRequest;
import mss301.bookingservice.dto.request.NoShowDecisionRequest;
import mss301.bookingservice.dto.request.NoShowReportRequest;
import mss301.bookingservice.dto.request.OccurrenceAcknowledgementRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BusyCaregiverResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    // Đánh dấu buổi bị bỏ lỡ.
    void markMissed(UUID id);

    // 2026-11-11: ca tự đóng mà trung tâm kết luận buổi không được thực hiện → CAREGIVER_MISSED (hoàn tiền khách, ghi
    // vắng ca). Gọi trong transaction đang giữ khóa booking.
    void markNotDelivered(Booking booking, BookingOccurrence occurrence, UUID actorId, String note, Instant now);

    // 2026-11-10 — cảnh báo sớm: buổi đã tới giờ mà caregiver chưa check-in (còn trong hạn check-in trễ) → báo caregiver,
    // trung tâm và khách một lần, trước khi job MISSED ghi nhận vắng ca.
    List<UUID> findLateCheckInIds();

    // Cảnh báo caregiver check-in trễ.
    void warnLateCheckIn(UUID id);

    // Lấy id buổi cần nhắc caregiver.
    List<UUID> findShiftReminderIds();

    // Nhắc caregiver trước giờ buổi.
    void remindShift(UUID id);

    // Nhắc manager báo vắng chưa quyết định (một lần mỗi buổi).
    List<UUID> findUndecidedNoShowIds();

    // D21 (H-10): báo vắng không được quyết sau giờ kết thúc + 24h → hệ thống tự chấp nhận, báo khách + trung tâm.
    void autoAcceptNoShow(UUID id);

    // Module 2 (SPEC V3): Trung tâm bố trí người thay thế khẩn cấp
    APIResponse<Void> emergencyReassign(UUID actorId, UUID centerId, UUID bookingId,
                                        EmergencyReassignmentRequest request);

    // Module 2 (SPEC V3): Khách phản hồi người thay thế khẩn cấp (ACCEPT, RESCHEDULE, REJECT_AND_CANCEL)
    // 2026-11-11: khách chủ booking báo nhanh về buổi. Buổi tới giờ chưa ai check-in → báo trung tâm; buổi COMPLETED trong
    // cửa sổ phản ánh → ca chuyển "chờ trung tâm xác nhận" (giữ quyết toán).
    APIResponse<OccurrenceResponse> reportByCustomer(UUID actorId, UUID occurrenceId, CustomerVisitReportRequest request);

    // Khách đồng ý / từ chối người thay thế cho buổi.
    APIResponse<Void> consentReplacement(UUID actorId, UUID occurrenceId,
                                         ReplacementConsentRequest request);

    // Module 3 (SPEC V3): Xem trước dời lịch buổi chăm sóc
    APIResponse<OccurrenceRescheduleResponse> reschedulePreview(
            UUID actorId, Set<RoleCode> roles, UUID centerId, UUID occurrenceId,
            OccurrenceReschedulePreviewRequest request);

    // Module 3 (SPEC V3): Thực hiện dời lịch buổi chăm sóc
    APIResponse<OccurrenceRescheduleResponse> reschedule(
            UUID actorId, Set<RoleCode> roles, UUID centerId, UUID occurrenceId,
            OccurrenceRescheduleRequest request);

    // 2026-11-13: booking CONFIRMED / IN_PROGRESS có buổi CAREGIVER_MISSED và không còn buổi đang chạy — chờ chốt sau khi
    // khoản hoàn buổi vắng (hẹn giờ) được gửi.
    List<UUID> findBookingsToFinalizeAfterMissedRefund();

    void finalizeAfterMissedRefund(UUID bookingId);

    // 2026-11-13: CENTER_MANAGER — buổi đã trả của trung tâm còn cần người chăm sóc, sớm nhất trước (theo trang).
    APIResponse<PageResponse<CaregiverOccurrenceResponse>> listUnassigned(UUID centerId, int page, int size);
}
