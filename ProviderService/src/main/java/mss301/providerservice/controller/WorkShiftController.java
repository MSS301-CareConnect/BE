package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Work Shifts", description = "Trung tâm công bố và quản lý ca làm việc")
public class WorkShiftController {
    private final WorkShiftService shiftService;
    private final ShiftRegistrationService registrationService;

    @PostMapping("/centers/{centerId}/shifts")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    @Operation(summary = "Tạo ca làm việc")
    public APIResponse<WorkShiftResponse> create(@CurrentUserId UUID actorId, @PathVariable UUID centerId,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.create(actorId, centerId, request);
    }

    @GetMapping("/shifts")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER','CUSTOMER')")
    @Operation(summary = "Tìm ca làm việc", description = "Lọc theo trung tâm, trạng thái và khoảng ngày.")
    public APIResponse<PageResponse<WorkShiftResponse>> list(
            @RequestParam(required = false) UUID centerId, @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) LocalDate fromDate, @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return shiftService.list(centerId, status, fromDate, toDate, page, size);
    }

    @GetMapping("/shifts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Chi tiết ca làm việc")
    public APIResponse<WorkShiftResponse> get(@PathVariable UUID id) { return shiftService.get(id); }

    @PutMapping("/shifts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    @Operation(summary = "Cập nhật ca làm việc")
    public APIResponse<WorkShiftResponse> update(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return shiftService.update(actorId, id, request);
    }

    @DeleteMapping("/shifts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    @Operation(summary = "Hủy và xóa mềm ca làm việc")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                    @RequestParam Long version) {
        return shiftService.delete(actorId, id, version);
    }

    @GetMapping("/shifts/{id}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    @Operation(summary = "Danh sách caregiver đăng ký ca")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> registrations(
            @CurrentUserId UUID actorId, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return registrationService.listForShift(actorId, id, page, size);
    }

    @PatchMapping("/registrations/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    @Operation(summary = "Duyệt đăng ký", description = "Khi APPROVED, hệ thống tự tạo caregiver schedule.")
    public APIResponse<ShiftRegistrationResponse> review(
            @CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return registrationService.review(actorId, id, request);
    }
}
