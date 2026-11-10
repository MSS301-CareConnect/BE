package mss301.carecatalogservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

// Query của GET /api/v1/catalog-items. CUSTOMER / CAREGIVER luôn chỉ thấy ACTIVE (status bị bỏ qua).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CatalogItemSearchRequest {
    ServiceStatus status;

    String category;

    String keyword;

    @Builder.Default
    int page = 0;

    @Builder.Default
    int size = 20;
}
