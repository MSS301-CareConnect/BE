package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.util.UUID;

// Dịch vụ phải làm trong ca — chỉ tên / đơn vị, không lộ giá cho caregiver.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceServiceResponse {
    UUID careServiceId;

    String serviceName;

    UnitType unitType;
}
