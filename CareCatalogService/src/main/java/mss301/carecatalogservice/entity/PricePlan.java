package mss301.carecatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.PricingUnit;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "price_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AttributeOverride(name = "id", column = @Column(name = "price_id"))
@SQLRestriction("deleted_at IS NULL")
public class PricePlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_service_id", nullable = false)
    CenterOffering centerOffering;

    @Column(name = "plan_name", nullable = false)
    String planName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_unit", nullable = false)
    PricingUnit pricingUnit;

    @Column(name = "duration_minutes", nullable = false)
    Integer durationMinutes;

    @Column(name = "base_price", nullable = false)
    BigDecimal basePrice;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;
}
