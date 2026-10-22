package mss301.carecatalogservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CenterOfferingResponse {
    UUID id;
    CatalogItemResponse catalogItem;
    UUID centerId;
    String description;
    ServiceStatus status;
    // Chỉ có khi duyệt dịch vụ của trung tâm (CAT-02); khách chỉ nhận gói đang active.
    List<PricePlanResponse> pricePlans;
}
