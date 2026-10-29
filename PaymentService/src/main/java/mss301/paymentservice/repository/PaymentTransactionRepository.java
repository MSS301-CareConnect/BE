package mss301.paymentservice.repository;

import mss301.paymentservice.entity.PaymentTransaction;
import mss301.paymentservice.enums.TransactionEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

// Append-only: chỉ save + đọc; không gọi delete / sửa.
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    boolean existsByIdempotencyKeyAndEventType(String idempotencyKey, TransactionEventType eventType);

    List<PaymentTransaction> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);
}
