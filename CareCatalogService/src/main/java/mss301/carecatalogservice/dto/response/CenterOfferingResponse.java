package mss301.carecatalogservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterOfferingResponse {
    UUID id;
    CatalogItemResponse catalogItem;
    UUID centerId;
    String description;
    ServiceStatus status;
}
