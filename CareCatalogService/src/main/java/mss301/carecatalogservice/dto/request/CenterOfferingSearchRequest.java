package mss301.carecatalogservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

// Query của GET /api/v1/center-services/center/{centerId}. status chỉ có tác dụng với ADMIN / manager của trung tâm đó;
// người khác luôn chỉ thấy dịch vụ đang bán được (offering + danh mục ACTIVE, có ít nhất một gói giá active).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterOfferingSearchRequest {
    ServiceStatus status;

    @Builder.Default
    int page = 0;

    @Builder.Default
    int size = 20;
}
