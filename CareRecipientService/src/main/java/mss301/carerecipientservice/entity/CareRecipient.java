package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.BloodType;
import mss301.carerecipientservice.enums.Gender;
import mss301.carerecipientservice.enums.IndependenceLevel;
import mss301.carerecipientservice.enums.MobilityLevel;
import mss301.carerecipientservice.enums.RecipientStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// Cột text: length chỉ để test H2 sinh varchar đủ dài; PostgreSQL dùng text theo schema/care_recipient.sql.
@Entity
@Table(name = "care_recipients")
@AttributeOverride(name = "id", column = @Column(name = "recipient_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareRecipient extends BaseEntity {
    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Column(name = "gender")
    @Enumerated(EnumType.STRING)
    Gender gender;

    @Column(name = "phone_number")
    String phoneNumber;

    @Column(name = "avatar_url", length = 1000)
    String avatarUrl;

    @Column(name = "address_line", length = 500)
    String addressLine;

    @Column(name = "ward")
    String ward;

    @Column(name = "district")
    String district;

    @Column(name = "province")
    String province;

    @Column(name = "latitude", precision = 9, scale = 6)
    BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    BigDecimal longitude;

    @Column(name = "blood_type")
    BloodType bloodType;

    @Column(name = "height_cm", precision = 5, scale = 2)
    BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    BigDecimal weightKg;

    @Column(name = "medical_history", length = 5000)
    String medicalHistory;

    @Column(name = "chronic_conditions", length = 5000)
    String chronicConditions;

    @Column(name = "allergies", length = 5000)
    String allergies;

    @Column(name = "mobility_level")
    @Enumerated(EnumType.STRING)
    MobilityLevel mobilityLevel;

    @Column(name = "independence_level")
    @Enumerated(EnumType.STRING)
    IndependenceLevel independenceLevel;

    @Column(name = "dietary_notes", length = 5000)
    String dietaryNotes;

    @Column(name = "sleep_habits", length = 5000)
    String sleepHabits;

    @Column(name = "behavior_notes", length = 5000)
    String behaviorNotes;

    @Column(name = "care_requirements", length = 5000)
    String careRequirements;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    RecipientStatus status;

    // userId bên IdentityService (tham chiếu ngoài, không FK).
    @Column(name = "created_by", nullable = false, updatable = false)
    UUID createdBy;

    @Column(name = "updated_by")
    UUID updatedBy;

    // Optimistic locking: PUT phải gửi version đang xem, lệch → 409.
    @Version
    @Column(name = "version", nullable = false)
    long version;
}
