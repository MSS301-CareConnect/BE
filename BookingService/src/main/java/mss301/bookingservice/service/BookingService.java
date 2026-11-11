package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.CaregiverHeldScheduleResponse;
import mss301.bookingservice.dto.response.RecipientActivityResponse;
import mss301.bookingservice.dto.request.AcceptRequest;
import mss301.bookingservice.dto.request.BookingCancelRequest;
import mss301.bookingservice.dto.request.CancellationPreviewRequest;
import mss301.bookingservice.dto.request.SystemCancellationRequest;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingQuoteRequest;
import mss301.bookingservice.dto.request.BookingSearchRequest;
import mss301.bookingservice.dto.request.CaregiverAssignRequest;
import mss301.bookingservice.dto.request.DepositPaidRequest;
import mss301.bookingservice.dto.request.ReasonRequest;
import mss301.bookingservice.dto.request.ServiceAddressRequest;
import mss301.bookingservice.dto.response.CaregiverBookingRequestResponse;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingHistoryResponse;
import mss301.bookingservice.dto.response.CancellationPreviewResponse;
import mss301.bookingservice.dto.response.SystemCancellationResponse;
import mss301.bookingservice.dto.response.BookingQuoteResponse;
import mss301.bookingservice.dto.response.BookingStatusCountsResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.PaymentContextResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BookingService {

    // Danh sách booking theo phạm vi người gọi.
    APIResponse<PageResponse<BookingSummaryResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                           BookingSearchRequest request);

    // Hàng đợi trung tâm: số booking theo trạng thái + hạn gần nhất / sắp quá hạn. Manager: trung tâm trong token;
    // admin: centerId (null = toàn hệ thống).
    APIResponse<BookingStatusCountsResponse> countByStatus(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId);

    // BK-05: lịch sử trạng thái booking + sự kiện từng buổi, sắp theo thời gian; phạm vi như get.
    APIResponse<PageResponse<BookingHistoryResponse>> listHistory(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                                  UUID id, int page, int size);

    // Chi tiết booking.
    APIResponse<BookingDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id);

    // Danh sách buổi của booking.
    APIResponse<PageResponse<OccurrenceResponse>> listOccurrences(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                                  UUID id, OccurrenceStatus status, int page, int size);

    // SERVICE (PaymentService, C3.2): dữ liệu giá / buổi để tạo phiên cọc và tính hoàn tiền.
    APIResponse<PaymentContextResponse> getPaymentContext(UUID id);

    // CUSTOMER xem chi phí tạm tính trước khi đặt (BK-01) — không ghi DB.
    APIResponse<BookingQuoteResponse> quote(UUID actorId, BookingQuoteRequest request);

    // CUSTOMER tạo booking; cùng Idempotency-Key + cùng nội dung → trả booking cũ.
    APIResponse<BookingDetailResponse> create(UUID actorId, String idempotencyKey, BookingCreateRequest request);

    // BK-03: CUSTOMER xem trước tiền hoàn nếu hủy (cả booking hoặc từng buổi) — không ghi DB.
    APIResponse<CancellationPreviewResponse> previewCancellation(UUID actorId, UUID id,
                                                                 CancellationPreviewRequest request);

    // BK-03 / BK-04: CUSTOMER (booking của mình) hoặc CENTER_MANAGER (trung tâm mình) hủy cả booking hoặc từng buổi chưa
    // diễn ra; đã đặt cọc → xếp hoàn tiền qua outbox. Không còn buổi nào → đóng booking.
    APIResponse<BookingDetailResponse> cancel(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id,
                                              BookingCancelRequest request);

    // BK-04 (SERVICE): khóa trung tâm → hủy + hoàn 100%; khóa caregiver → đánh dấu buổi cần đổi người. Idempotent.
    APIResponse<SystemCancellationResponse> cancelBySystem(SystemCancellationRequest request);

    // Trung tâm nhận booking.
    APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request);

    // Trung tâm từ chối booking.
    APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request);

    // Phân công / đổi caregiver cho booking.
    APIResponse<BookingDetailResponse> updateCaregiver(UUID actorId, UUID centerId, UUID id, CaregiverAssignRequest request);

    // Đổi caregiver cho một buổi.
    APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID id, UUID occurrenceId,
                                                              CaregiverAssignRequest request);

    // CR-2: CUSTOMER (booking của mình) hoặc CENTER_MANAGER (trung tâm mình) đổi địa chỉ chăm sóc cho các buổi chưa bắt
    // đầu; không đổi khi đang có buổi IN_PROGRESS. Báo khách, trung tâm và caregiver được giao.
    APIResponse<BookingDetailResponse> updateServiceAddress(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id,
                                                            ServiceAddressRequest request);

    // CR-3: CAREGIVER — đơn khách gửi thẳng cho mình đang chờ trả lời.
    APIResponse<PageResponse<CaregiverBookingRequestResponse>> listCaregiverRequests(UUID actorId, int page, int size);

    // Chi tiết một đơn còn chờ caregiver trả lời (cùng phạm vi dữ liệu với danh sách).
    APIResponse<CaregiverBookingRequestResponse> getCaregiverRequest(UUID actorId, UUID id);

    // CR-3: CAREGIVER nhận đơn → AWAITING_INITIAL_PAYMENT (không qua trung tâm).
    APIResponse<CaregiverBookingRequestResponse> acceptAsCaregiver(UUID actorId, UUID id);

    // CR-3: CAREGIVER từ chối — SHIFT → REJECTED; RECURRING / CUSTOM → trung tâm phân người khác.
    APIResponse<CaregiverBookingRequestResponse> declineAsCaregiver(UUID actorId, UUID id, ReasonRequest request);

    // SERVICE (CareRecipientService): caregiver có được xem hồ sơ người bệnh không — tính theo từng buổi.
    APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId);

    // Số booking chưa kết thúc của người bệnh.
    APIResponse<RecipientActivityResponse> getRecipientActivity(UUID recipientId);

    // Spec §3: lịch caregiver đã bị booking giữ trong khoảng ngày (mặc định 31 ngày từ hôm nay).
    APIResponse<CaregiverHeldScheduleResponse> listHeldSchedules(UUID caregiverId, LocalDate from, LocalDate to);

    // Ghi nhận đã trả cọc.
    APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request);

    // SERVICE (PaymentService): khách đã trả xong phần còn lại (spec §9.1).
    APIResponse<BookingDetailResponse> markRemainingPaid(UUID id, DepositPaidRequest request);

    // SERVICE (PaymentService): quá hạn trả phần còn lại → EXPIRED (M1); Payment đã giữ cọc theo policy (M3).
    APIResponse<BookingDetailResponse> markPaymentOverdue(UUID id);

    // ─── SCHEDULER ───────────────────────────────────────────────────────────

    // Lấy id booking quá hạn xử lý.
    List<UUID> findExpirableIds();

    // Booking chờ cọc đã qua hạn + grace — job riêng chu kỳ ngắn để nhả slot giữ chỗ sớm (2026-11-09).
    List<UUID> findExpiredHoldIds();

    // Q-B2 (2026-11-09): booking đã cọc còn buổi chưa có người khi sát giờ (unassigned-cancel-lead) → hủy buổi đó, hoàn 100 %.
    List<UUID> findUnassignedDueIds();

    // Hủy booking quá hạn mà chưa phân công được caregiver.
    void cancelUnassigned(UUID id);

    // Chuyển booking sang hết hạn.
    void expire(UUID id);

    // Lấy id booking cần nhắc trả phần còn lại.
    List<UUID> findRemainingReminderIds();

    // Nhắc trả phần còn lại.
    void remindRemainingPayment(UUID id);

    // Lấy id booking cần nhắc trung tâm.
    List<UUID> findCenterReminderIds();

    // Nhắc trung tâm xử lý booking.
    void remindCenterAction(UUID id);
}
