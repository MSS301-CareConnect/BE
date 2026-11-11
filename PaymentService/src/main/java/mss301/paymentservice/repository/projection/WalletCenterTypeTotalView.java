package mss301.paymentservice.repository.projection;

import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

// Tổng biến động theo (trung tâm, loại chủ ví, loại biến động) — tính số dư theo trung tâm đã tạo ra khoản tiền.
public interface WalletCenterTypeTotalView {
    UUID getCenterId();

    WalletOwnerType getOwnerType();

    WalletTransactionType getType();

    BigDecimal getTotalAmount();
}
