package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByBookingIdAndPaymentType(UUID bookingId, PaymentType paymentType);

    List<Payment> findAllByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    // Khóa payment khi cộng refundedAmount (dispatcher) — hai lượt hoàn cùng payment không ghi đè nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findForUpdate(@Param("id") UUID id);

    @Query("""
            select count(p) from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt <= :to
              and (:centerId is null or p.centerId = :centerId)
            """)
    long countPaid(@Param("status") PaymentStatus status, @Param("from") Instant from, @Param("to") Instant to,
                   @Param("centerId") UUID centerId);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt <= :to
              and (:centerId is null or p.centerId = :centerId)
            """)
    BigDecimal sumPaid(@Param("status") PaymentStatus status, @Param("from") Instant from, @Param("to") Instant to,
                       @Param("centerId") UUID centerId);

    @Query("""
            select coalesce(sum(p.refundedAmount), 0) from Payment p
            where p.status = :status and p.paidAt >= :from and p.paidAt <= :to
              and (:centerId is null or p.centerId = :centerId)
            """)
    BigDecimal sumRefunded(@Param("status") PaymentStatus status, @Param("from") Instant from, @Param("to") Instant to,
                           @Param("centerId") UUID centerId);
}
