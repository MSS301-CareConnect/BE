package mss301.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "permissions")
@AttributeOverride(name = "id", column = @Column(name = "permission_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Permission extends BaseEntity {
    @Column(name = "permission_code", nullable = false, unique = true)
    String permissionCode;

    @Column(name = "permission_name", nullable = false)
    String permissionName;

    @Column(name = "resource", nullable = false)
    String resource;

    @Column(name = "description")
    String description;
}
