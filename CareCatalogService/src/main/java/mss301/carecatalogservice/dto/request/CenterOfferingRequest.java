package mss301.carecatalogservice.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterOfferingRequest {

    @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
    @JsonAlias({"careServiceId", "catalogItemId"})
    UUID catalogItemId;

    // Tùy chọn: nếu client gửi thì phải khớp với center_id trong token của CENTER_MANAGER
    UUID centerId;

    String description;

    @NotNull(message = "CARE_SERVICE_STATUS_REQUIRED")
    ServiceStatus status;

    // M-04: version lúc người dùng mở form; có thì phải khớp bản đang lưu (khác → 409). Bỏ trống = client cũ, không kiểm.
    Long version;
}
