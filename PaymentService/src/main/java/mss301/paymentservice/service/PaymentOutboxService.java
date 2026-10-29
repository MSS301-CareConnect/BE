package mss301.paymentservice.service;

import mss301.paymentservice.entity.Payment;

import java.util.List;
import java.util.UUID;

// Outbox xác nhận cọc sang BookingService (Đợt 1b): IPN chỉ ghi nhận tiền, việc báo Booking tin cậy qua outbox.
public interface PaymentOutboxService {
    // Trong transaction của IPN; gửi ngay sau commit, lỗi thì scheduler gửi lại.
    void enqueueDepositConfirmation(Payment payment, String paymentReference);

    List<UUID> findDueIds();

    void process(UUID outboxId);
}
