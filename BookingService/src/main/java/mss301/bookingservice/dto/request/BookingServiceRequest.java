package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Một dịch vụ khách chọn. Chỉ nhận id — tên / giá lấy từ CareCatalogService (không tin client).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingServiceRequest {
    @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
    UUID careServiceId;

    UUID centerServiceId;

    // Bắt buộc (D12): khách chọn đúng gói giá; Catalog không tự chọn gói.
    @NotNull(message = "PRICE_PLAN_REQUIRED")
    UUID pricePlanId;
}
