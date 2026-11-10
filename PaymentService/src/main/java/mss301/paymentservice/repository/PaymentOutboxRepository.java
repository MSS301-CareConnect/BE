package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.paymentservice.entity.PaymentOutbox;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import mss301.paymentservice.util.LockHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, UUID> {

    boolean existsByEventTypeAndPaymentId(OutboxEventType eventType, UUID paymentId);

    Optional<PaymentOutbox> findByEventTypeAndPaymentId(OutboxEventType eventType, UUID paymentId);

    @Query("select o.id from PaymentOutbox o where o.status = :status and o.nextAttemptAt <= :now order by o.nextAttemptAt")
    List<UUID> findDueIds(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    // "Nhận" việc: scheduler và lượt gửi ngay sau commit không cùng gửi một dòng; dòng đang bị khóa thì bỏ qua.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = LockHints.LOCK_TIMEOUT, value = LockHints.SKIP_LOCKED))
    @Query("select o from PaymentOutbox o where o.id = :id")
    Optional<PaymentOutbox> findForUpdateSkipLocked(@Param("id") UUID id);

    // Ghi kết quả: chờ khóa (không bỏ qua) để không mất kết quả gửi.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOutbox o where o.id = :id")
    Optional<PaymentOutbox> findForUpdate(@Param("id") UUID id);
}
