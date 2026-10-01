package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(name = "uk_roles_code", columnNames = "role_code"))
@AttributeOverride(name = "id", column = @Column(name = "role_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Role extends BaseEntity {
    @Column(name = "role_code", nullable = false)
    String roleCode;

    @Column(name = "role_name", nullable = false)
    String roleName;

    @Column(name = "description")
    String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    boolean active = true;

    @OneToMany(mappedBy = "role")
    @Builder.Default
    Set<RolePermission> rolePermissions = new HashSet<>();
}
