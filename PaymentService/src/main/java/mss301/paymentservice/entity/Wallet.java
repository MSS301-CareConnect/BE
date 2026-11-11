package mss301.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.WalletOwnerType;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

// Ví của một chủ (khách / caregiver / trung tâm / nền tảng). Số dư chỉ đổi qua WalletService.post (khóa dòng + sổ giao dịch).
@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallets_owner",
        columnNames = {"owner_type", "owner_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Wallet extends BaseEntity {
    @Id
    @UuidGenerator
    @Column(name = "wallet_id")
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    WalletOwnerType ownerType;

    // userId (CUSTOMER / CAREGIVER), centerId (CENTER), UUID 0 (PLATFORM).
    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Builder.Default
    @Column(name = "available_balance", nullable = false, precision = 14, scale = 2)
    BigDecimal availableBalance = BigDecimal.ZERO;

    // Đang chờ rút (đã trừ khỏi khả dụng, chưa chuyển khoản).
    @Builder.Default
    @Column(name = "held_balance", nullable = false, precision = 14, scale = 2)
    BigDecimal heldBalance = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    Long version;
}
