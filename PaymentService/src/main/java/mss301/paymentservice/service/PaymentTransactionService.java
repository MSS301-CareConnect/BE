package mss301.paymentservice.service;

import mss301.paymentservice.enums.TransactionEventType;

import java.math.BigDecimal;
import java.util.UUID;

// Ghi nhật ký payment_transactions — luôn trong transaction của biến động tiền (append-only).
public interface PaymentTransactionService {
    void record(UUID bookingId, UUID paymentId, UUID refundId, UUID invoiceId, TransactionEventType eventType,
                BigDecimal amount, String idempotencyKey, String detail, Boolean signatureValid);
}
