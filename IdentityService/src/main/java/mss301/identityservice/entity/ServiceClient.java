package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Danh tính máy (client-credentials) của các service nghiệp vụ — token SERVICE (IAM-01).
// Tắt tạm thời bằng active=false; xóa hẳn khỏi hệ thống bằng xóa mềm (deleted_at) như mọi entity.
@Entity
@Table(name = "service_clients")
@AttributeOverride(name = "id", column = @Column(name = "client_uuid"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceClient extends BaseEntity {
    @Column(name = "client_id", nullable = false, length = 50)
    String clientId;

    @ToString.Exclude
    @Column(name = "secret_hash", nullable = false)
    String secretHash;

    @Builder.Default
    @Column(name = "active", nullable = false)
    boolean active = true;

    @Column(name = "description")
    String description;
}
