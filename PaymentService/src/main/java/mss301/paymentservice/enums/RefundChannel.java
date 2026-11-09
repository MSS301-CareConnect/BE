package mss301.paymentservice.enums;

// Nơi nhận tiền hoàn — đồng bộ CHECK refunds.channel. WALLET: khách tự hủy (CUSTOMER_CANCEL) → cộng ví khách, không gọi cổng.
public enum RefundChannel {
    GATEWAY,
    WALLET
}
