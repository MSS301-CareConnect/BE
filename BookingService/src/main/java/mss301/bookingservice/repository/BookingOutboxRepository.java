package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.util.LockHints;
import org.springframework.data.domain.Page;
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
public interface BookingOutboxRepository extends JpaRepository<BookingOutbox, UUID> {

    @Query("""
            select o.id from BookingOutbox o
            where o.status = :status and o.nextAttemptAt <= :now and o.deletedAt is null
            order by o.nextAttemptAt
            """)
    List<UUID> findDueIds(@Param("status") OutboxStatus status, @Param("now") Instant now, Pageable pageable);

    // Khóa dòng khi "nhận" việc: scheduler và lượt gửi ngay sau commit không cùng gửi một dòng. SKIP LOCKED (BK-09):
    // dòng đang được instance khác nhận thì bỏ qua ngay, không đứng chờ khóa.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = LockHints.LOCK_TIMEOUT, value = LockHints.SKIP_LOCKED))
    @Query("select o from BookingOutbox o where o.id = :id and o.deletedAt is null")
    Optional<BookingOutbox> findForUpdate(@Param("id") UUID id);

    List<BookingOutbox> findAllByAggregateIdAndDeletedAtIsNull(UUID aggregateId);

    boolean existsByIdempotencyKeyAndDeletedAtIsNull(String idempotencyKey);

    // H-08: màn hình sự cố admin.
    Page<BookingOutbox> findByStatusAndDeletedAtIsNull(OutboxStatus status, Pageable pageable);

    Page<BookingOutbox> findByDeletedAtIsNull(Pageable pageable);
}
