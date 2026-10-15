package mss301.carecatalogservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.dto.request.InternalServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.InternalServiceQuoteResponse;
import mss301.carecatalogservice.service.CatalogQuoteService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/center-services")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Internal - Catalog", description = "Báo giá dịch vụ nội bộ cho BookingService. Bảo vệ bởi X-Internal-Secret.")
public class InternalCatalogController {

    CatalogQuoteService catalogQuoteService;

    @PostMapping("/quote")
    public APIResponse<List<InternalServiceQuoteResponse>> quote(@Valid @RequestBody InternalServiceQuoteRequest request) {
        return APIResponse.success("Báo giá dịch vụ thành công", catalogQuoteService.quote(request));
    }
}
