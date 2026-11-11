package mss301.paymentservice.service;

import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.WalletWithdrawal;

import java.util.List;
import java.util.UUID;

// Outbox báo BookingService (Đợt 1b): IPN / job chỉ ghi nhận tiền, việc báo Booking tin cậy qua outbox.
public interface PaymentOutboxService {
    // Trong transaction của IPN; gửi ngay sau commit, lỗi thì scheduler gửi lại.
    void enqueueDepositConfirmation(Payment payment, String paymentReference);

    // Spec §9.1: đã trả xong phần còn lại trước buổi đầu (trong transaction IPN).
    void enqueueRemainingConfirmation(Payment payment, String paymentReference);

    // M1: quá hạn trả phần còn lại → Booking EXPIRED (trong transaction job quá hạn; payment = khoản đầu).
    void enqueuePaymentOverdue(Payment depositPayment, UUID invoiceId);

    // 2026-11-10: mọi dòng hoàn của một lệnh (cùng idempotency key) đã REFUNDED → một thông báo cho khách (gọi trong
    // transaction ghi kết quả hoàn, booking_financials đã khóa nên chỉ một lượt thấy "đã xong hết").
    void enqueueRefundCompleted(Refund refund);

    // 2026-11-10: yêu cầu rút tiền đã chuyển khoản xong / bị từ chối → báo người rút (trong transaction đổi trạng thái).
    void enqueueWithdrawalResult(WalletWithdrawal withdrawal);

    // Lấy id outbox tới hạn gửi.
    List<UUID> findDueIds();

    // Gửi một dòng outbox.
    void process(UUID outboxId);
}
