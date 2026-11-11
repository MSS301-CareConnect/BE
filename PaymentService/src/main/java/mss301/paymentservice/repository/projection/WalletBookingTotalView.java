package mss301.paymentservice.repository.projection;

import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.util.UUID;

// Tổng biến động của một booking theo (buổi, loại chủ ví, loại biến động) — quyết toán booking.
public interface WalletBookingTotalView {
    UUID getOccurrenceId();

    WalletOwnerType getOwnerType();

    WalletTransactionType getType();

    BigDecimal getTotalAmount();
}
