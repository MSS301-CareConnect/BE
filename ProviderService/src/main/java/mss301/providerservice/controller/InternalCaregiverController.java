package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.InternalCaregiverResponse;
import mss301.providerservice.service.InternalProviderService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/caregivers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Caregivers", description = "Cho Booking gọi nội bộ với header X-Internal-Secret; gateway chặn /internal/**.")
public class InternalCaregiverController {
    InternalProviderService internalProviderService;

    @GetMapping("/{caregiverId}")
    public APIResponse<InternalCaregiverResponse> get(@PathVariable UUID caregiverId) {
        return internalProviderService.getCaregiver(caregiverId);
    }

    @GetMapping("/by-user/{userId}")
    public APIResponse<InternalCaregiverResponse> getByUser(@PathVariable UUID userId) {
        return internalProviderService.getCaregiverByUser(userId);
    }
}
