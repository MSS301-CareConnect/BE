package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.*;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.CaregiverScheduleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Center Schedules", description = "Trung tâm theo dõi và hủy lịch caregiver")
@PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
public class CenterScheduleController {
    private final CaregiverScheduleService scheduleService;

    @GetMapping("/centers/{centerId}/schedules")
    @Operation(summary = "Danh sách lịch của trung tâm")
    public APIResponse<PageResponse<ScheduleResponse>> list(
            @CurrentUserId UUID actorId, @PathVariable UUID centerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return scheduleService.centerSchedules(actorId, centerId, page, size);
    }

    @PatchMapping("/schedules/{id}/status")
    @Operation(summary = "Cập nhật/hủy lịch caregiver")
    public APIResponse<ScheduleResponse> update(
            @CurrentUserId UUID actorId, @PathVariable UUID id,
            @Valid @RequestBody ScheduleStatusRequest request) {
        return scheduleService.updateStatus(actorId, id, request);
    }
}
