package mss301.bookingservice.service;

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
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingHistoryResponse;
import mss301.bookingservice.dto.response.CancellationPreviewResponse;
import mss301.bookingservice.dto.response.SystemCancellationResponse;
import mss301.bookingservice.dto.response.BookingQuoteResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.PaymentContextResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Booking — một API cho mọi role. Controller chỉ kiểm role bằng @PreAuthorize; phạm vi dữ liệu chọn ở đây theo role:
 * ADMIN mọi booking, CENTER_MANAGER booking cùng center_id trong token, CUSTOMER booking của mình. Ngoài phạm vi → 404.
 * Buổi chăm sóc (lịch caregiver, chi tiết buổi) ở BookingOccurrenceService.
 */
public interface BookingService {

    APIResponse<PageResponse<BookingSummaryResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                           BookingSearchRequest request);

    // BK-05: lịch sử trạng thái booking + sự kiện từng buổi, sắp theo thời gian; phạm vi như get.
    APIResponse<PageResponse<BookingHistoryResponse>> listHistory(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                                  UUID id, int page, int size);

    APIResponse<BookingDetailResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id);

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

    APIResponse<BookingDetailResponse> accept(UUID actorId, UUID centerId, UUID id, AcceptRequest request);

    APIResponse<BookingDetailResponse> reject(UUID actorId, UUID centerId, UUID id, ReasonRequest request);

    APIResponse<BookingDetailResponse> updateCaregiver(UUID actorId, UUID centerId, UUID id, CaregiverAssignRequest request);

    APIResponse<OccurrenceResponse> updateOccurrenceCaregiver(UUID actorId, UUID centerId, UUID id, UUID occurrenceId,
                                                              CaregiverAssignRequest request);

    // SERVICE (CareRecipientService): caregiver có được xem hồ sơ người bệnh không — tính theo từng buổi.
    APIResponse<CaregiverAccessResponse> checkCaregiverAccess(UUID caregiverUserId, UUID recipientId);

    APIResponse<BookingDetailResponse> markDepositPaid(UUID id, DepositPaidRequest request);

    // ─── SCHEDULER ───────────────────────────────────────────────────────────

    List<UUID> findExpirableIds();

    void expire(UUID id);
}
