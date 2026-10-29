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
import mss301.paymentservice.enums.InvoiceStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Hóa đơn phần còn lại sau khi booking COMPLETED (PAY-11); quá hạn → OVERDUE = khách đang nợ.
@Entity
@Table(name = "remaining_invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RemainingInvoice extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "invoice_id")
    UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    UUID bookingId;

    @Column(name = "customer_id", nullable = false)
    UUID customerId;

    @Column(name = "center_id", nullable = false)
    UUID centerId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "due_at", nullable = false)
    Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InvoiceStatus status;

    @Column(name = "payment_id")
    UUID paymentId;

    @Column(name = "paid_at")
    Instant paidAt;

    @Column(name = "waived_by")
    UUID waivedBy;

    @Column(name = "waive_reason", length = 500)
    String waiveReason;

    @Version
    @Column(nullable = false)
    Long version;
}
