package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Bản sao (rút gọn) CenterOfferingResponse của CareCatalogService GET /api/v1/center-services/{id};
// chỉ giữ field Provider cần để kiểm kỹ năng caregiver — pricePlans… bị bỏ qua.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CenterServiceClientResponse {
    UUID id;

    CatalogItemClientResponse catalogItem;

    UUID centerId;

    String status;
}
