package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.enums.UserStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
}
