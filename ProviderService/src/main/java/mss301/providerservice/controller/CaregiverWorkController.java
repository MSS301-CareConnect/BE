package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.*;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Caregiver Work", description = "Caregiver đăng ký ca và theo dõi lịch làm")
@PreAuthorize("hasRole('CAREGIVER')")
public class CaregiverWorkController {
    private final ShiftRegistrationService registrationService;
    private final CaregiverScheduleService scheduleService;
    private final CaregiverService caregiverService;

    @GetMapping("/caregiver/profile")
    @Operation(summary = "Hồ sơ caregiver của tôi")
    public APIResponse<CaregiverResponse> myProfile(@CurrentUserId UUID actorId) {
        return caregiverService.myProfile(actorId);
    }

    @PostMapping("/caregiver/shifts/{shiftId}/registrations")
    @Operation(summary = "Đăng ký ca làm")
    public APIResponse<ShiftRegistrationResponse> register(
            @CurrentUserId UUID actorId, @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRegistrationRequest request) {
        return registrationService.register(actorId, shiftId, request);
    }

    @GetMapping("/caregiver/registrations")
    @Operation(summary = "Danh sách đăng ký ca của tôi")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> registrations(
            @CurrentUserId UUID actorId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return registrationService.myRegistrations(actorId, page, size);
    }

    @DeleteMapping("/caregiver/registrations/{id}")
    @Operation(summary = "Hủy đăng ký đang chờ duyệt")
    public APIResponse<Void> cancel(@CurrentUserId UUID actorId, @PathVariable UUID id,
                                    @RequestParam Long version) {
        return registrationService.cancelOwn(actorId, id, version);
    }

    @GetMapping("/caregiver/schedules")
    @Operation(summary = "Lịch làm việc của tôi")
    public APIResponse<PageResponse<ScheduleResponse>> schedules(
            @CurrentUserId UUID actorId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return scheduleService.mySchedules(actorId, page, size);
    }

    @PatchMapping("/caregiver/schedules/{id}/status")
    @Operation(summary = "Bắt đầu hoặc hoàn thành lịch làm")
    public APIResponse<ScheduleResponse> updateSchedule(
            @CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }
}
