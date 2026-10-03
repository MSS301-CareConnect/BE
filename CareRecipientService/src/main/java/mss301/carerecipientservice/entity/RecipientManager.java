package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recipient_managers",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipient_managers_user", columnNames = {"recipient_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientManager {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "recipient_manager_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    CareRecipient recipient;

    // userId bên IdentityService (không FK, đã xác minh qua Feign khi thêm).
    @Column(name = "user_id", nullable = false, updatable = false)
    UUID userId;

    @Column(name = "relationship_type", nullable = false)
    @Enumerated(EnumType.STRING)
    RelationshipType relationshipType;

    // Đúng một người quản lý chính / hồ sơ, luôn là OWNER (partial unique index + CHECK trong SQL).
    @Column(name = "is_primary", nullable = false)
    boolean primary;

    @Column(name = "access_level", nullable = false)
    @Enumerated(EnumType.STRING)
    RecipientAccessLevel accessLevel;

    @Column(name = "added_by", updatable = false)
    UUID addedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
