package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.InvitationStatus;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Lời mời quản lý hồ sơ (C-04 / D19). inviteeUserId chỉ có khi email thuộc tài khoản CUSTOMER đang hoạt động — người mời
 * không bao giờ biết điều này; lời mời không có invitee thì không ai chấp nhận được và tự hết hạn.
 */
@Entity
@Table(name = "recipient_manager_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManagerInvitation {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "invitation_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    CareRecipient recipient;

    // Email đã chuẩn hóa (trim + lowercase) người mời nhập — chỉ người mời thấy lại.
    @Column(name = "email", nullable = false, updatable = false)
    String email;

    @Column(name = "invitee_user_id", updatable = false)
    UUID inviteeUserId;

    @Column(name = "relationship_type", nullable = false)
    @Enumerated(EnumType.STRING)
    RelationshipType relationshipType;

    @Column(name = "access_level", nullable = false)
    @Enumerated(EnumType.STRING)
    RecipientAccessLevel accessLevel;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    InvitationStatus status;

    @Column(name = "invited_by", nullable = false, updatable = false)
    UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "responded_at")
    Instant respondedAt;

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
