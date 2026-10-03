package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "emergency_contacts")
@AttributeOverride(name = "id", column = @Column(name = "contact_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EmergencyContact extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    CareRecipient recipient;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "relationship", nullable = false, length = 100)
    String relationship;

    @Column(name = "phone_number", nullable = false)
    String phoneNumber;

    // Tối đa một liên hệ chính / hồ sơ (partial unique index uk_emergency_contacts_primary).
    @Column(name = "is_primary", nullable = false)
    boolean primary;

    // Số nhỏ gọi trước.
    @Column(name = "priority", nullable = false)
    int priority;
}
