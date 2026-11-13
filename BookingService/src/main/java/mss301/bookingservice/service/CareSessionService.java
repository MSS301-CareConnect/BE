package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CareSessionCenterCheckInRequest;
import mss301.bookingservice.dto.request.CareSessionEndRequest;
import mss301.bookingservice.dto.request.CareSessionForceEndRequest;
import mss301.bookingservice.dto.request.CareSessionReviewRequest;
import mss301.bookingservice.dto.request.CareSessionStartRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.request.CareTaskCreateRequest;
import mss301.bookingservice.dto.request.CareTaskUpdateRequest;
import mss301.bookingservice.dto.request.SessionNoteRequest;
import mss301.bookingservice.dto.response.CareTaskResponse;
import mss301.bookingservice.dto.response.SessionNoteResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.dto.response.CareSessionResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.enums.SessionCompletionReview;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CareSessionService {
    // Chi tiết ca chăm sóc.
    APIResponse<CareSessionResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id);

    // OPS-02: caregiver được giao check-in buổi (từ 30 phút trước giờ bắt đầu tới trước giờ kết thúc), sinh checklist.
    APIResponse<CareSessionResponse> start(UUID actorId, CareSessionStartRequest request);

    // 2026-11-13: caregiver quên bấm check-in → manager cùng trung tâm ghi nhận hộ (cùng điều kiện như check-in thường,
    // giờ đến có thể lùi về trong khung check-in), kèm ghi chú vào ca và báo caregiver.
    APIResponse<CareSessionResponse> checkInByCenter(UUID actorId, UUID centerId, CareSessionCenterCheckInRequest request);

    // OPS-04: caregiver check-out khi không còn việc PENDING; buổi COMPLETED; chốt booking nếu là buổi cuối.
    APIResponse<CareSessionResponse> end(UUID actorId, UUID id, CareSessionEndRequest request);

    // OPS-04: manager trung tâm buộc kết thúc ca caregiver quên đóng — việc PENDING thành SKIPPED.
    APIResponse<CareSessionResponse> forceEnd(UUID actorId, UUID centerId, UUID id, CareSessionForceEndRequest request);

    // 2026-11-11: ca hệ thống tự đóng chờ trung tâm xác nhận (theo trang) và kết luận của trung tâm.
    APIResponse<PageResponse<CareSessionResponse>> listForReview(UUID centerId, SessionCompletionReview review, int page,
                                                                 int size);

    // Trung tâm xác nhận kết quả ca.
    APIResponse<CareSessionResponse> review(UUID actorId, UUID centerId, UUID id, CareSessionReviewRequest request);

    // Cập nhật công việc trong checklist.
    APIResponse<CareTaskResponse> updateTask(UUID actorId, UUID sessionId, UUID taskId, CareTaskUpdateRequest request);

    // Thêm công việc vào checklist.
    APIResponse<CareTaskResponse> createTask(UUID actorId, UUID sessionId, CareTaskCreateRequest request);

    // Thêm ghi chú tình trạng.
    APIResponse<SessionNoteResponse> createNote(UUID actorId, UUID sessionId, SessionNoteRequest request);

    // Danh sách ghi chú của ca.
    APIResponse<PageResponse<SessionNoteResponse>> listNotes(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id, int page, int size);

    // Job nhắc session còn mở quá giờ kết thúc buổi (một lần / session).
    List<UUID> findOverdueIds();

    // Nhắc ca quá giờ chưa kết thúc.
    void remindOverdue(UUID id);

    // D21 (H-10): ca còn IN_PROGRESS quá giờ kết thúc buổi + autoCloseAfter.
    List<UUID> findAutoCloseIds();

    // Tự đóng ca bị quên check-out.
    void autoClose(UUID id);
}
