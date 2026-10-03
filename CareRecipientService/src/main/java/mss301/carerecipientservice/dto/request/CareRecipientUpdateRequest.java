package mss301.carerecipientservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.BloodType;
import mss301.carerecipientservice.enums.Gender;
import mss301.carerecipientservice.enums.IndependenceLevel;
import mss301.carerecipientservice.enums.MobilityLevel;

import java.math.BigDecimal;
import java.time.LocalDate;

// PUT thay thế toàn bộ thông tin hồ sơ: field null = xóa giá trị. Không có status/manager (API riêng).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareRecipientUpdateRequest {
    @NotBlank(message = "FULLNAME_REQUIRED")
    @Size(max = 255, message = "INVALID_FULLNAME")
    String fullName;

    @PastOrPresent(message = "INVALID_DATE_OF_BIRTH")
    LocalDate dateOfBirth;

    Gender gender;

    @Pattern(regexp = "[0-9]{10,11}", message = "INVALID_PHONE")
    String phoneNumber;

    @Size(max = 1000, message = "INVALID_AVATAR_URL")
    @Pattern(regexp = "https?://\\S+", message = "INVALID_AVATAR_URL")
    String avatarUrl;

    @Size(max = 500, message = "TEXT_TOO_LONG")
    String addressLine;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String ward;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String district;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String province;

    @DecimalMin(value = "-90", message = "INVALID_COORDINATES")
    @DecimalMax(value = "90", message = "INVALID_COORDINATES")
    @Digits(integer = 3, fraction = 6, message = "INVALID_COORDINATES")
    BigDecimal latitude;

    @DecimalMin(value = "-180", message = "INVALID_COORDINATES")
    @DecimalMax(value = "180", message = "INVALID_COORDINATES")
    @Digits(integer = 3, fraction = 6, message = "INVALID_COORDINATES")
    BigDecimal longitude;

    BloodType bloodType;

    @DecimalMin(value = "0", inclusive = false, message = "INVALID_MEASUREMENT")
    @DecimalMax(value = "300", message = "INVALID_MEASUREMENT")
    @Digits(integer = 3, fraction = 2, message = "INVALID_MEASUREMENT")
    BigDecimal heightCm;

    @DecimalMin(value = "0", inclusive = false, message = "INVALID_MEASUREMENT")
    @DecimalMax(value = "500", message = "INVALID_MEASUREMENT")
    @Digits(integer = 3, fraction = 2, message = "INVALID_MEASUREMENT")
    BigDecimal weightKg;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String medicalHistory;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String chronicConditions;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String allergies;

    MobilityLevel mobilityLevel;

    IndependenceLevel independenceLevel;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String dietaryNotes;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String sleepHabits;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String behaviorNotes;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String careRequirements;

    // Version đang xem (optimistic locking) — lệch với DB → 409 RECIPIENT_VERSION_CONFLICT.
    @NotNull(message = "VERSION_REQUIRED")
    Long version;
}
