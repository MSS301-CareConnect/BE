package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.BloodType;
import mss301.carerecipientservice.enums.Gender;
import mss301.carerecipientservice.enums.IndependenceLevel;
import mss301.carerecipientservice.enums.MobilityLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// Dữ liệu cho Booking / caregiver được phân công (API nội bộ). Không có lịch sử bệnh đầy đủ ngoài các field cần chăm sóc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientSnapshotResponse {
    UUID recipientId;

    String fullName;

    LocalDate dateOfBirth;

    Integer age;

    Gender gender;

    String phoneNumber;

    String addressLine;

    String ward;

    String district;

    String province;

    BigDecimal latitude;

    BigDecimal longitude;

    BloodType bloodType;

    MobilityLevel mobilityLevel;

    IndependenceLevel independenceLevel;

    String allergies;

    String chronicConditions;

    String dietaryNotes;

    String behaviorNotes;

    String careRequirements;

    RecipientStatus status;

    List<MedicationResponse> activeMedications;

    // null khi hồ sơ chưa có liên hệ chính
    EmergencyContactResponse primaryEmergencyContact;

    // Thời điểm chụp — Booking lưu nguyên snapshot này lúc đặt lịch
    Instant snapshotAt;
}
