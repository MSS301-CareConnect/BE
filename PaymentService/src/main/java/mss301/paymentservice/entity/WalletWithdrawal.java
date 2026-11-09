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
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Yêu cầu rút tiền từ ví: số tiền bị tạm giữ tới khi admin chuyển khoản (COMPLETED) hoặc từ chối (REJECTED → hoàn lại).
@Entity
@Table(name = "wallet_withdrawals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletWithdrawal extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "withdrawal_id")
    UUID id;

    @Column(name = "wallet_id", nullable = false)
    UUID walletId;

    @Column(name = "requester_user_id", nullable = false)
    UUID requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    WalletOwnerType ownerType;

    // Ví CENTER: trung tâm của manager tạo yêu cầu (admin hiển thị tên trung tâm).
    @Column(name = "center_id")
    UUID centerId;

    @Column(nullable = false, precision = 14, scale = 2)
    BigDecimal amount;

    @Column(name = "bank_name", nullable = false, length = 100)
    String bankName;

    @Column(name = "bank_account_number", nullable = false, length = 30)
    String bankAccountNumber;

    @Column(name = "account_holder_name", nullable = false, length = 100)
    String accountHolderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    WalletWithdrawalStatus status;

    @Column(name = "reject_reason", length = 500)
    String rejectReason;

    @Column(name = "reviewed_by")
    UUID reviewedBy;

    @Column(name = "reviewed_at")
    Instant reviewedAt;

    // Mã giao dịch ngân hàng admin nhập khi xác nhận đã chuyển.
    @Column(name = "transfer_reference", length = 100)
    String transferReference;

    @Column(name = "completed_by")
    UUID completedBy;

    @Column(name = "completed_at")
    Instant completedAt;

    @Version
    @Column(nullable = false)
    Long version;
}
