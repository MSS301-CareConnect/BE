package mss301.paymentservice.enums;

// Yêu cầu rút tiền — đồng bộ CHECK wallet_withdrawals.status. PENDING → APPROVED → COMPLETED (admin đã chuyển khoản);
// PENDING | APPROVED → REJECTED (hoàn lại số dư tạm giữ).
public enum WalletWithdrawalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED
}
