package mss301.carecatalogservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carecatalogservice.enums.PricingUnit;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    // Giảm theo % trên base_price (0 = không giảm). Khách trả finalPrice — báo giá cho Booking lấy đơn giá này.
    @Column(name = "discount_percent", nullable = false)
    @Builder.Default
    Integer discountPercent = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;

    // Giá sau giảm (dẫn xuất, không phải cột). Không đặt getXxx để không thành thuộc tính — mapper gọi qua expression.
    public BigDecimal finalPrice() {
        return applyDiscount(basePrice, discountPercent);
    }

    // VND làm tròn đến đồng; giữ một công thức cho cả entity lẫn projection giá thấp nhất.
    public static BigDecimal applyDiscount(BigDecimal basePrice, Integer discountPercent) {
        if (basePrice == null) {
            return null;
        }
        int discount = discountPercent == null ? 0 : discountPercent;
        return basePrice.multiply(BigDecimal.valueOf(100L - discount))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}
