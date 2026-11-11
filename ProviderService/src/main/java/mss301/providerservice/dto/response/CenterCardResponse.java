package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterCardResponse {
    UUID centerId;

    String centerName;

    String address;

    String operatingHours;

    String phoneNumber;

    List<String> imageUrls;

    BigDecimal minPrice;

    Integer serviceCount;
}
