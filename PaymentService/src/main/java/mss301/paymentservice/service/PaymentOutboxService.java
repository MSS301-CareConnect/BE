package mss301.paymentservice.service;

import mss301.paymentservice.entity.Payment;

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

    List<UUID> findDueIds();

    void process(UUID outboxId);
}
