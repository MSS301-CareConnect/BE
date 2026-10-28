package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.Gender;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Thẻ kết quả tìm caregiver cho khách (SRCH-01) — không có userId / trạng thái / audit nội bộ.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverCardResponse {
    UUID caregiverId;
    String fullName;
    Gender gender;
    Integer experienceYears;
    BigDecimal ratingAverage;
    Integer ratingCount;
    String avatarUrl;
    List<UUID> careServiceIds;
    long verifiedCertificateCount;
}
