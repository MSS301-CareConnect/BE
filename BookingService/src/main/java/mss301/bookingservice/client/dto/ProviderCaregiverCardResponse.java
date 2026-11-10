package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Một ứng viên của GET /api/v1/caregivers/search (ProviderService) — chỉ các field Booking dùng để xếp hạng tự phân công.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderCaregiverCardResponse {
    UUID caregiverId;
    UUID centerId;
    BigDecimal ratingAverage;
    Integer ratingCount;
    Integer experienceYears;
}
