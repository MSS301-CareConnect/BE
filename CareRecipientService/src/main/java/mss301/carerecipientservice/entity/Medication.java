package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.MedicationRoute;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medications")
@AttributeOverride(name = "id", column = @Column(name = "medication_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Medication extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    CareRecipient recipient;

    @Column(name = "medication_name", nullable = false)
    String medicationName;

    @Column(name = "dosage")
    String dosage;

    // Mô tả tự do, ví dụ "2 lần/ngày".
    @Column(name = "frequency")
    String frequency;

    // Danh sách giờ uống dạng "HH:mm", lưu jsonb (mảng).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "time_of_day")
    List<String> timeOfDay;

    @Column(name = "route")
    @Enumerated(EnumType.STRING)
    MedicationRoute route;

    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "end_date")
    LocalDate endDate;

    @Column(name = "prescribed_by")
    String prescribedBy;

    @Column(name = "usage_notes", length = 5000)
    String usageNotes;

    @Column(name = "is_active", nullable = false)
    boolean active;

    @Column(name = "updated_by")
    UUID updatedBy;
}
