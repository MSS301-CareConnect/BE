package mss301.paymentservice.repository;

import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.enums.PaymentStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByBookingId(UUID bookingId);

    Page<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByBookingId(UUID bookingId, Pageable pageable);

    Page<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    @Query("select count(p) from Payment p where p.status=mss301.paymentservice.enums.PaymentStatus.SUCCESS " +
            "and p.paidAt between :from and :to and (:centerId is null or p.centerId=:centerId)")
    long successfulCount(@Param("from") Instant from, @Param("to") Instant to, @Param("centerId") UUID centerId);

    @Query("select coalesce(sum(p.amount),0) from Payment p " +
            "where p.status=mss301.paymentservice.enums.PaymentStatus.SUCCESS and p.paidAt between :from and :to " +
            "and (:centerId is null or p.centerId=:centerId)")
    BigDecimal totalRevenue(@Param("from") Instant from, @Param("to") Instant to, @Param("centerId") UUID centerId);
}
