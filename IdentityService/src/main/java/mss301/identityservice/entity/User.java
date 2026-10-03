package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.enums.UserStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@AttributeOverride(name = "id", column = @Column(name = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity {
    @Column(name = "email", nullable = false)
    String email;

    @Column(name = "password")
    String password;

    @Column(name = "full_name", nullable = false)
    String fullName;

    @Column(name = "phone_number")
    String phoneNumber;

    @Column(name = "user_status", nullable = false)
    @Enumerated(EnumType.STRING)
    UserStatus status;

    // External center identity; no cross-service FK or center business data.
    @Column(name = "center_id")
    UUID centerId;

    @Column(name = "authz_version", nullable = false)
    long authzVersion;

    @OneToMany(mappedBy = "user")
    @Builder.Default
    Set<UserRole> userRoles = new HashSet<>();

    // Quyền hiệu lực chỉ tính role còn active + chưa xóa mềm — dùng cho kiểm quyền (service) lẫn response (mapper).
    // Không đặt tên getXxx để không thành thuộc tính của entity với MapStruct/Jackson.
    public Set<String> effectiveRoleCodes() {
        return effectiveRoles()
                .map(Role::getRoleCode)
                .collect(Collectors.toSet());
    }

    public Set<String> effectivePermissionCodes() {
        return effectiveRoles()
                .flatMap(role -> role.activePermissionCodes().stream())
                .collect(Collectors.toSet());
    }

    private Stream<Role> effectiveRoles() {
        return userRoles.stream()
                .map(UserRole::getRole)
                .filter(role -> role.isActive() && role.getDeletedAt() == null);
    }
}
