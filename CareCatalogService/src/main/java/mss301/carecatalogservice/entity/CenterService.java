package mss301.carecatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "center_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "center_services_id"))
@SQLRestriction("deleted_at IS NULL")
public class CenterService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_services_id", nullable = false)
    CareService careService;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "description", length = 1000)
    String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ServiceStatus status;

}
