package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.enums.BookingFinancialStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Tài chính của một booking phía Payment; khóa dòng này (findForUpdate) để tuần tự mọi thao tác tiền của booking.
@Entity
@Table(name = "booking_financials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingFinancial extends BaseEntity {
    @Id
    @Column(name = "booking_id")
    UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(name = "policy_id", nullable = false)
    UUID policyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false)
    PolicySnapshot policySnapshot;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 14, scale = 2)
    BigDecimal depositAmount;

    @Column(name = "deposit_payment_id")
    UUID depositPaymentId;

    @Column(name = "deposit_paid_at")
    Instant depositPaidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    BookingFinancialStatus status;

    @Version
    @Column(nullable = false)
    Long version;
}
