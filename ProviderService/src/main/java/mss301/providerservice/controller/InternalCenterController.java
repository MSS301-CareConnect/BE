package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.InternalCenterResponse;
import mss301.providerservice.service.InternalProviderService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/centers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Centers", description = "Cho Booking gọi nội bộ với header X-Internal-Secret; gateway chặn /internal/**.")
public class InternalCenterController {
    InternalProviderService internalProviderService;

    @GetMapping("/{centerId}")
    public APIResponse<InternalCenterResponse> get(@PathVariable UUID centerId) {
        return internalProviderService.getCenter(centerId);
    }
}
