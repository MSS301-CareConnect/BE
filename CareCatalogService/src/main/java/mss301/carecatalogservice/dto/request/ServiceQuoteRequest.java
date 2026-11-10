package mss301.carecatalogservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceQuoteRequest {

    @NotNull(message = "CENTER_ID_REQUIRED")
    UUID centerId;

    @NotEmpty(message = "SERVICES_REQUIRED")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {
        @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
        UUID careServiceId;

        UUID centerServiceId;

        // Bắt buộc (D12): khách chọn đúng gói giá, không để Catalog tự chọn gói "đầu tiên".
        @NotNull(message = "PRICE_PLAN_ID_REQUIRED")
        UUID pricePlanId;
    }
}
