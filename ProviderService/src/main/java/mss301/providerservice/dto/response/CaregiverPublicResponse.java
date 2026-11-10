package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.Gender;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Hồ sơ công khai (SRCH-01): không có userId, ngày sinh, trạng thái xác minh, người duyệt, version, audit.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverPublicResponse {
    UUID caregiverId;
    UUID centerId;
    String centerName;
    String fullName;
    Gender gender;
    Integer experienceYears;
    String bio;
    BigDecimal ratingAverage;
    Integer ratingCount;
    String avatarUrl;
    List<CaregiverPublicServiceResponse> services;
    List<PublicCertificateResponse> certificates;
}
