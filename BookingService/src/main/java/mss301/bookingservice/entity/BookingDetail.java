package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.UnitType;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dịch vụ trong booking. Tên / đơn vị / giá lấy từ CareCatalogService (TODO: chưa nối → NULL, không tin client).
 * Ghi một lần khi tạo booking nên chỉ có created_at (không kế thừa BaseEntity).
 */
@Entity
@Table(name = "booking_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingDetail {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "booking_detail_id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    Booking booking;

    @Column(name = "care_service_id", nullable = false)
    UUID careServiceId;

    @Column(name = "center_service_id")
    UUID centerServiceId;

    // price_plans.price_id bên CareCatalogService (giá theo HOUR/DAY/SHIFT nằm ở bảng giá)
    @Column(name = "price_plan_id")
    UUID pricePlanId;

    @Column(name = "service_name")
    String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", length = 20)
    UnitType unitType;

    @Column(name = "unit_price", precision = 14, scale = 2)
    BigDecimal unitPrice;

    @Column(name = "quantity")
    Integer quantity;

    @Column(name = "total_price", precision = 14, scale = 2)
    BigDecimal totalPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
