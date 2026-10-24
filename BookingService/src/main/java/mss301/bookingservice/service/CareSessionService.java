package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.CareSessionEndRequest;
import mss301.bookingservice.dto.request.CareSessionForceEndRequest;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ca chăm sóc thực tế (care_sessions + care_tasks). Phạm vi đọc: caregiver của ca, khách chủ booking, manager cùng trung
 * tâm, ADMIN / SERVICE; ngoài phạm vi → 404.
 */
public interface CareSessionService {
    APIResponse<CareSessionResponse> get(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId, UUID id);

    // OPS-02: caregiver được giao check-in buổi (từ 30 phút trước giờ bắt đầu tới trước giờ kết thúc), sinh checklist.
    APIResponse<CareSessionResponse> start(UUID actorId, CareSessionStartRequest request);

    // OPS-04: caregiver check-out khi không còn việc PENDING; buổi COMPLETED; chốt booking nếu là buổi cuối.
    APIResponse<CareSessionResponse> end(UUID actorId, UUID id, CareSessionEndRequest request);

    // OPS-04: manager trung tâm buộc kết thúc ca caregiver quên đóng — việc PENDING thành SKIPPED.
    APIResponse<CareSessionResponse> forceEnd(UUID actorId, UUID centerId, UUID id, CareSessionForceEndRequest request);

    APIResponse<CareTaskResponse> updateTask(UUID actorId, UUID sessionId, UUID taskId, CareTaskUpdateRequest request);

    APIResponse<CareTaskResponse> createTask(UUID actorId, UUID sessionId, CareTaskCreateRequest request);

    APIResponse<SessionNoteResponse> createNote(UUID actorId, UUID sessionId, SessionNoteRequest request);

    APIResponse<PageResponse<SessionNoteResponse>> listNotes(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id, int page, int size);

    // Job nhắc session còn mở quá giờ kết thúc buổi (một lần / session).
    List<UUID> findOverdueIds();

    void remindOverdue(UUID id);
}
