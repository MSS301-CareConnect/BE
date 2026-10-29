package mss301.carecatalogservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CatalogItemRequest {
    @NotBlank(message = "CARE_SERVICE_NAME_REQUIRED")
    String name;

    String description;

    @NotBlank(message = "CARE_SERVICE_CATEGORY_REQUIRED")
    String category;

    @NotNull(message = "CARE_SERVICE_STATUS_REQUIRED")
    ServiceStatus status;

    // M-04: version lúc người dùng mở form; có thì phải khớp bản đang lưu (khác → 409). Bỏ trống = client cũ, không kiểm.
    Long version;
}
