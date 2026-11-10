package mss301.providerservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Bản sao (rút gọn) CatalogItemResponse của CareCatalogService — id là careServiceId (dịch vụ chuẩn).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CatalogItemClientResponse {
    UUID id;

    String name;

    String status;
}
