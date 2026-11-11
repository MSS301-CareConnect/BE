package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CareSessionResponse;
import mss301.bookingservice.dto.response.CareTaskResponse;
import mss301.bookingservice.dto.response.SessionNoteResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.enums.SessionCompletionReview;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CareSessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-sessions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Care session", description = "Ca chăm sóc thực tế — check-in, checklist, check-out, buộc kết thúc")
public class CareSessionController {

    CareSessionService careSessionService;

    // Ca hệ thống tự đóng của trung tâm theo trạng thái xem lại (mặc định: đang chờ xác nhận).
    @GetMapping
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<PageResponse<CareSessionResponse>> listForReview(
            @CurrentCenterId UUID centerId,
            @RequestParam(defaultValue = "PENDING") SessionCompletionReview review,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return careSessionService.listForReview(centerId, review, page, size);
    }

    // API xem chi tiết ca chăm sóc.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CAREGIVER', 'CUSTOMER', 'CENTER_MANAGER', 'ADMIN', 'SERVICE')")
    public APIResponse<CareSessionResponse> get(@CurrentUserId UUID actorId,
                                                @CurrentRoles Set<RoleCode> roles,
                                                @CurrentCenterId(required = false) UUID scopeCenterId,
                                                @PathVariable UUID id) {
        return careSessionService.get(actorId, roles, scopeCenterId, id);
    }

    // API danh sách ghi chú của ca chăm sóc.
    @GetMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('CAREGIVER','CUSTOMER','CENTER_MANAGER','ADMIN')")
    public APIResponse<PageResponse<SessionNoteResponse>> listNotes(@CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles, @CurrentCenterId(required = false) UUID centerId, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return careSessionService.listNotes(actorId, roles, centerId, id, page, size);
    }

    // API thêm công việc vào checklist của ca.
    @PostMapping("/{id}/tasks")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareTaskResponse> createTask(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                   @Valid @RequestBody CareTaskCreateRequest request) {
        return careSessionService.createTask(actorId, id, request);
    }

    // API thêm ghi chú tình trạng trong ca.
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<SessionNoteResponse> createNote(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                     @Valid @RequestBody SessionNoteRequest request) {
        return careSessionService.createNote(actorId, id, request);
    }

    // API caregiver bắt đầu ca (check-in).
    @PostMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareSessionResponse> start(@CurrentUserId UUID actorId,
                                                  @Valid @RequestBody CareSessionStartRequest request) {
        return careSessionService.start(actorId, request);
    }

    // API caregiver kết thúc ca (check-out).
    @PostMapping("/{id}/end")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareSessionResponse> end(@CurrentUserId UUID actorId,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody(required = false) CareSessionEndRequest request) {
        return careSessionService.end(actorId, id, request);
    }

    // API trung tâm xác nhận kết quả ca chăm sóc.
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CareSessionResponse> review(@CurrentUserId UUID actorId,
                                                   @CurrentCenterId UUID centerId,
                                                   @PathVariable UUID id,
                                                   @Valid @RequestBody CareSessionReviewRequest request) {
        return careSessionService.review(actorId, centerId, id, request);
    }

    // API trung tâm buộc kết thúc ca đang treo.
    @PostMapping("/{id}/force-end")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<CareSessionResponse> forceEnd(@CurrentUserId UUID actorId,
                                                     @CurrentCenterId UUID centerId,
                                                     @PathVariable UUID id,
                                                     @Valid @RequestBody CareSessionForceEndRequest request) {
        return careSessionService.forceEnd(actorId, centerId, id, request);
    }
    // API cập nhật trạng thái công việc trong checklist.
    @PatchMapping("/{id}/tasks/{taskId}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CareTaskResponse> updateTask(@CurrentUserId UUID actorId, @PathVariable UUID id,
            @PathVariable UUID taskId, @Valid @RequestBody CareTaskUpdateRequest request) {
        return careSessionService.updateTask(actorId, id, taskId, request);
    }
}
