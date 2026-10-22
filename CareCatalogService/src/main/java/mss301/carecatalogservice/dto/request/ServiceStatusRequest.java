package mss301.carecatalogservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

// PATCH .../status — bật / tắt dịch vụ chuẩn hoặc dịch vụ trung tâm (vô hiệu hóa an toàn thay cho xóa).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceStatusRequest {
    @NotNull(message = "CARE_SERVICE_STATUS_REQUIRED")
    ServiceStatus status;
}
