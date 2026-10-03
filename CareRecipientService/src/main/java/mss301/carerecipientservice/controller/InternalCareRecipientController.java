package mss301.carerecipientservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.dto.request.*;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.AccessCheckResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.service.InternalCareRecipientService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/care-recipients")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Care recipient", description = "Cho Booking/Caregiver gọi nội bộ với header X-Internal-Secret; gateway chặn /internal/**.")
public class InternalCareRecipientController {
    InternalCareRecipientService internalCareRecipientService;

    @GetMapping("/{id}/snapshot")
    public APIResponse<RecipientSnapshotResponse> getSnapshot(@PathVariable UUID id) {
        return internalCareRecipientService.getSnapshot(id);
    }

    @GetMapping("/{id}/access")
    public APIResponse<AccessCheckResponse> checkAccess(@PathVariable UUID id,
                                                        @RequestParam UUID userId,
                                                        @RequestParam(defaultValue = "VIEWER") RecipientAccessLevel minLevel) {
        return internalCareRecipientService.checkAccess(id, userId, minLevel);
    }

    @PostMapping("/batch")
    public APIResponse<List<CareRecipientSummaryResponse>> list(@Valid @RequestBody RecipientBatchRequest request) {
        return internalCareRecipientService.list(request);
    }
}
