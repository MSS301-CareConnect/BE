package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    // Mã permission còn hiệu lực của role (bỏ permission đã xóa mềm). Không đặt tên getXxx để không thành thuộc tính
    // của entity với MapStruct/Jackson.
    public Set<String> activePermissionCodes() {
        return rolePermissions.stream()
                .map(RolePermission::getPermission)
                .filter(permission -> permission.getDeletedAt() == null)
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());
    }
}
