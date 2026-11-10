package mss301.providerservice.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CenterStatus;

import java.util.UUID;

@Entity
@Table(name = "care_centers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "center_id"))
public class CareCenter extends BaseEntity {

    @Column(name = "center_name", nullable = false)
    String centerName;

    @Column(name = "phone_number", length = 30)
    String phoneNumber;

    // Duy nhất giữa trung tâm chưa xóa mềm — partial unique index uk_care_centers_email_active (M-05).
    @Column(name = "email", nullable = false)
    String email;

    @Column(name = "address", nullable = false, columnDefinition = "text")
    String address;

    @Column(name = "operating_hours", length = 500)
    String operatingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "center_status", nullable = false, length = 50)
    CenterStatus centerStatus;

    @Column(name = "status_reason", length = 500)
    String statusReason;

    /** UUID tài khoản CENTER_MANAGER bên IdentityService; không có cross-service foreign key. */
    @Column(name = "manager_user_id")
    UUID managerUserId;

    @Column(name = "created_by", nullable = false)
    UUID createdBy;

    @Column(name = "updated_by")
    UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    Long version;
}
