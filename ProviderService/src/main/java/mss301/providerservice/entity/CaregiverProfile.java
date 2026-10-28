package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.Gender;
import mss301.providerservice.enums.VerificationStatus;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "caregiver_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "caregiver_id"))
public class CaregiverProfile extends BaseEntity {
    @Column(name = "user_id", nullable = false, unique = true)
    UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false)
    CareCenter center;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 30)
    Gender gender;

    @Column(name = "date_of_birth")
    LocalDate dateOfBirth;

    @Column(name = "experience_years", nullable = false)
    Integer experienceYears;

    @Column(name = "bio", columnDefinition = "text")
    String bio;

    @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
    BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    Integer ratingCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 50)
    VerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "caregiver_status", nullable = false, length = 50)
    CaregiverStatus caregiverStatus;

    @Column(name = "verification_reason", length = 500)
    String verificationReason;

    @Column(name = "verified_by")
    UUID verifiedBy;

    @Column(name = "verified_at")
    Instant verifiedAt;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "updated_by")
    UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;

    // Kỹ năng (caregiver_services) — chỉ đọc qua aggregate để dựng response; ghi bằng CaregiverSkillRepository.
    // BatchSize: danh sách caregiver nạp kỹ năng theo lô thay vì một query mỗi caregiver.
    @OneToMany(mappedBy = "caregiver", fetch = FetchType.LAZY)
    @BatchSize(size = 100)
    @OrderBy("assignedAt ASC")
    @Builder.Default
    List<CaregiverSkill> skills = new ArrayList<>();
}
