package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-shifts")
@Tag(name = "Work Shifts", description = "Quản lý ca làm việc và đăng ký ca")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkShiftController {

    WorkShiftService shiftService;
    ShiftRegistrationService registrationService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Tìm ca làm việc (lọc theo trung tâm, trạng thái và khoảng ngày)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')")
    public APIResponse<PageResponse<WorkShiftResponse>> list(
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftService.list(centerId, status, fromDate, toDate, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết ca làm việc")
    @PreAuthorize("isAuthenticated()")
    public APIResponse<WorkShiftResponse> get(@PathVariable UUID id) {
        return shiftService.get(id);
    }

    @GetMapping("/registrations/me")
    @Operation(summary = "Danh sách đăng ký ca của tôi (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> myRegistrations(
            @CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationService.myRegistrations(actorId, page, size);
    }

    @GetMapping("/{id}/registrations")
    @Operation(summary = "Danh sách caregiver đăng ký ca (Center Manager / Admin)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listRegistrationsForShift(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationService.listForShift(actorId, id, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    @PostMapping("/centers/{centerId}")
    @Operation(summary = "Tạo ca làm việc cho trung tâm")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.create(actorId, centerId, request);
    }

    @PostMapping("/{shiftId}/registrations")
    @Operation(summary = "Caregiver đăng ký ca làm việc")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<ShiftRegistrationResponse> register(
            @CurrentUserId UUID actorId,
            @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRegistrationRequest request) {
        return registrationService.register(actorId, shiftId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật ca làm việc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.update(actorId, id, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    @PatchMapping("/registrations/{id}/review")
    @Operation(summary = "Duyệt hoặc từ chối đăng ký ca (Center Manager / Admin)")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ShiftRegistrationResponse> reviewRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return registrationService.review(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @Operation(summary = "Hủy và xóa mềm ca làm việc")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return shiftService.delete(actorId, id, version);
    }

    @DeleteMapping("/registrations/{id}")
    @Operation(summary = "Hủy đăng ký ca đang chờ duyệt (Caregiver)")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<Void> cancelRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam Long version) {
        return registrationService.cancelOwn(actorId, id, version);
    }
}
