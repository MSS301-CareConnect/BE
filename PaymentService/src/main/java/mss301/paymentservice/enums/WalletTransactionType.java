package mss301.paymentservice.enums;

// Loại biến động ví — đồng bộ CHECK wallet_transactions.type. Hệ số cho biết số dư khả dụng / tạm giữ tăng (+1), giảm (−1)
// hay không đổi (0) theo amount (luôn dương).
public enum WalletTransactionType {
    REFUND_CREDIT(1, 0),
    FORFEIT_CREDIT(1, 0),
    SERVICE_SETTLEMENT(1, 0),
    COMMISSION_CREDIT(1, 0),
    COMPLAINT_DEBIT(-1, 0),
    WITHDRAWAL_HOLD(-1, 1),
    WITHDRAWAL_RELEASE(1, -1),
    WITHDRAWAL_PAID(0, -1);

    private final int availableSign;
    private final int heldSign;

    WalletTransactionType(int availableSign, int heldSign) {
        this.availableSign = availableSign;
        this.heldSign = heldSign;
    }

    public int availableSign() {
        return availableSign;
    }

    public int heldSign() {
        return heldSign;
    }
}
