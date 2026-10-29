package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.entity.PaymentAttempt;
import mss301.paymentservice.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    @Query("select a from PaymentAttempt a join fetch a.payment where a.id = :id")
    Optional<PaymentAttempt> findWithPaymentById(@Param("id") UUID id);

    @Query("select a from PaymentAttempt a join fetch a.payment where a.reference = :reference")
    Optional<PaymentAttempt> findByReference(@Param("reference") String reference);

    @Query("select a from PaymentAttempt a join fetch a.payment where a.providerCheckoutId = :checkoutId")
    Optional<PaymentAttempt> findByProviderCheckoutId(@Param("checkoutId") String checkoutId);

    // Khóa phiên khi xử lý IPN / webhook: hai callback trùng nhau không cùng ghi nhận thành công.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a join fetch a.payment where a.reference = :reference")
    Optional<PaymentAttempt> findForUpdateByReference(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a join fetch a.payment where a.providerCheckoutId = :checkoutId")
    Optional<PaymentAttempt> findForUpdateByCheckoutId(@Param("checkoutId") String checkoutId);

    // Phiên đã thu tiền theo thứ tự thu — phiên đầu là giao dịch gốc của payment (hoàn tiền thường đi qua nó).
    @Query("select a from PaymentAttempt a where a.payment.id = :paymentId and a.status = :status order by a.completedAt asc")
    List<PaymentAttempt> findAllCompletedByPayment(@Param("paymentId") UUID paymentId,
                                                   @Param("status") PaymentStatus status);

    // Job hết hạn: phiên PENDING đã quá hạn (kèm thời gian chờ IPN đến trễ).
    @Query("select a.id from PaymentAttempt a where a.status = :status and a.expiresAt <= :before order by a.expiresAt")
    List<UUID> findStaleIds(@Param("status") PaymentStatus status, @Param("before") Instant before, Pageable pageable);

    @Query("select a from PaymentAttempt a where a.payment.id = :paymentId and a.status = :status order by a.createdAt desc")
    List<PaymentAttempt> findAllByPaymentAndStatus(@Param("paymentId") UUID paymentId,
                                                   @Param("status") PaymentStatus status);
}
