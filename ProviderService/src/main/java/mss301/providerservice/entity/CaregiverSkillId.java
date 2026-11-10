package mss301.providerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.util.UUID;

// Khóa ghép của caregiver_services: một offering của trung tâm chỉ gán một lần cho mỗi caregiver.
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverSkillId implements Serializable {
    @Column(name = "caregiver_id", nullable = false)
    UUID caregiverId;

    @Column(name = "center_service_id", nullable = false)
    UUID centerServiceId;
}
