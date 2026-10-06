package mss301.carecatalogservice.dto.request;

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
public class CenterServiceRequest {

    @NotNull(message = "CARE_SERVICE_ID_REQUIRED")
    UUID careServiceId;
    
    // Tùy chọn: nếu client gửi thì phải khớp với center_id trong token của CENTER_MANAGER
    UUID centerId;

    String description;
    
    @NotNull(message = "CARE_SERVICE_STATUS_REQUIRED")
    ServiceStatus status;
}
