package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.security.CurrentUserId;
import mss301.carerecipientservice.service.CaregiverRecipientService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/caregiver/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Caregiver - Care recipient", description = "Caregiver xem hồ sơ người bệnh khi BookingService xác nhận đang được phân công (booking CONFIRMED / IN_PROGRESS). Không được phân công → 404.")
public class CaregiverCareRecipientController {
    CaregiverRecipientService caregiverRecipientService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<RecipientSnapshotResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return caregiverRecipientService.getSnapshot(actorId, id);
    }
}
