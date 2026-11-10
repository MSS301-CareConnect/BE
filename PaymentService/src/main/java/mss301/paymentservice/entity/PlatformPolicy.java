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
import mss301.paymentservice.dto.response.RefundTier;
import mss301.paymentservice.enums.PolicyScope;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Chính sách nền tảng có hiệu lực từ effectiveFrom (PAY-02 tối thiểu, D15) — chưa có API ghi, seed bằng SQL.
@Entity
@Table(name = "platform_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlatformPolicy extends BaseEntity {
    @Id
    @Column(name = "policy_id")
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    PolicyScope scope;

    @Column(name = "center_id")
    UUID centerId;

    @Column(name = "effective_from", nullable = false)
    Instant effectiveFrom;

    @Column(name = "deposit_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal depositPercent;

    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal commissionPercent;

    @Column(name = "payment_session_ttl_minutes", nullable = false)
    int paymentSessionTtlMinutes;

    @Column(name = "deposit_due_hours", nullable = false)
    int depositDueHours;

    @Column(name = "cooling_off_minutes", nullable = false)
    int coolingOffMinutes;

    @Column(name = "cooling_off_min_lead_hours", nullable = false)
    int coolingOffMinLeadHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "refund_tiers", nullable = false)
    List<RefundTier> refundTiers;

    @Column(name = "customer_no_show_charge_percent", nullable = false, precision = 5, scale = 2)
    BigDecimal customerNoShowChargePercent;

    @Column(name = "remaining_due_hours", nullable = false)
    int remainingDueHours;

    @Column(length = 500)
    String note;

    @Version
    @Column(nullable = false)
    Long version;
}
