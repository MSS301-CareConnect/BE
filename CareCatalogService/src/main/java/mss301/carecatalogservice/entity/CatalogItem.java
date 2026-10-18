package mss301.carecatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.ServiceStatus;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "care_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "care_services_id"))
@SQLRestriction("deleted_at IS NULL")
public class CatalogItem extends BaseEntity {

    @Column(name = "name", nullable = false)
    String name;

    @Column(name = "description", length = 1000)
    String description;

    @Column(name = "category", nullable = false)
    String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ServiceStatus status;
}
