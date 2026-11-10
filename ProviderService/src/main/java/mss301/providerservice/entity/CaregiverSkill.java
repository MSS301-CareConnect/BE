package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

/**
 * Kỹ năng của caregiver = offering của trung tâm (CareCatalogService) mà caregiver được phép làm (PRV-01).
 * Entity con của aggregate CaregiverProfile (chỉ có repository). Bảng liên kết: thay toàn bộ = xóa dòng cũ + thêm dòng
 * mới, không xóa mềm. {@code care_service_id} (dịch vụ chuẩn) lưu kèm để Booking so khớp không cần gọi Catalog.
 */
@Entity
@Table(name = "caregiver_services", indexes = @Index(
        name = "idx_caregiver_services_care_service", columnList = "care_service_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverSkill {
    @EmbeddedId
    CaregiverSkillId id;

    @MapsId("caregiverId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    CaregiverProfile caregiver;

    @Column(name = "care_service_id", nullable = false)
    UUID careServiceId;

    // Tên dịch vụ chuẩn lúc gán (snapshot) — GET kỹ năng không phải gọi Catalog.
    @Column(name = "service_name")
    String serviceName;

    @Column(name = "assigned_by", nullable = false)
    UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    Instant assignedAt;
}
