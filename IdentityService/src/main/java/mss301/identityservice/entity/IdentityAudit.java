package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

// Nhật ký chỉ ghi thêm (append-only) nên không kế thừa BaseEntity: không có updatedAt/deletedAt.
@Entity
@Table(name = "identity_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityAudit {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id")
    UUID id;

    @Column(name = "actor_id")
    UUID actorId;

    @Column(name = "target_id")
    UUID targetId;

    @Column(name = "center_id")
    UUID centerId;

    @Column(name = "action", nullable = false)
    String action;

    @Column(name = "detail", nullable = false)
    String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
