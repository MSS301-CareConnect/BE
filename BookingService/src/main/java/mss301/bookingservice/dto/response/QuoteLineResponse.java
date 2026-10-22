package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;

import java.math.BigDecimal;
import java.util.UUID;

// Một dòng dịch vụ trong báo giá: đơn giá × số đơn vị mỗi buổi × số buổi.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteLineResponse {
    UUID careServiceId;

    String serviceName;

    UnitType pricingUnit;

    BigDecimal unitPrice;

    Integer unitsPerOccurrence;

    Integer occurrenceCount;

    BigDecimal lineTotal;
}
