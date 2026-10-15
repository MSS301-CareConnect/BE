package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.CaregiverOccurrenceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/caregiver/occurrences")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver - Occurrence", description = "Lịch chăm sóc được giao cho caregiver đang đăng nhập (booking đã xác nhận).")
public class CaregiverOccurrenceController {
    CaregiverOccurrenceService caregiverOccurrenceService;

    @GetMapping
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<List<CaregiverOccurrenceResponse>> list(@CurrentUserId UUID actorId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                               @RequestParam(required = false) OccurrenceStatus status) {
        return caregiverOccurrenceService.list(actorId, from, to, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<CaregiverOccurrenceResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return caregiverOccurrenceService.get(actorId, id);
    }
}
