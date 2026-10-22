package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.util.LockHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Booking> findByIdAndCenterIdAndDeletedAtIsNull(UUID id, UUID centerId);

    Optional<Booking> findByCustomerIdAndIdempotencyKeyAndDeletedAtIsNull(UUID customerId, String idempotencyKey);

    boolean existsByBookingCode(String bookingCode);

    long countByCustomerIdAndStatusInAndDeletedAtIsNull(UUID customerId, Collection<BookingStatus> statuses);

    // Khóa dòng booking khi đổi trạng thái / caregiver: accept, hủy, đặt cọc, hết hạn chạy song song không cùng vượt
    // qua bước kiểm trạng thái (state machine) trên cùng một booking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id and b.deletedAt is null")
    Optional<Booking> findForUpdate(@Param("id") UUID id);

    // BK-04: booking còn giữ chỗ của một trung tâm (khóa trung tâm → hủy từng booking).
    @Query("""
            select b.id from Booking b
            where b.centerId = :centerId
              and b.deletedAt is null
              and b.status in :statuses
            order by b.createdAt
            """)
    List<UUID> findIdsByCenter(@Param("centerId") UUID centerId,
                               @Param("statuses") Collection<BookingStatus> statuses);

    // Scheduler (hết hạn, MISSED) chạy nhiều instance — BK-09: dòng đang bị instance khác khóa thì bỏ qua (Optional rỗng)
    // thay vì chờ; lượt quét sau sẽ thấy lại nếu điều kiện vẫn đúng.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = LockHints.LOCK_TIMEOUT, value = LockHints.SKIP_LOCKED))
    @Query("select b from Booking b where b.id = :id and b.deletedAt is null")
    Optional<Booking> findForUpdateSkipLocked(@Param("id") UUID id);

    // Booking PENDING quá hạn duyệt hoặc AWAITING_DEPOSIT quá hạn cọc (scheduler xử lý theo lô).
    @Query("""
            select b.id from Booking b
            where b.deletedAt is null
              and ((b.status = :pendingStatus and b.acceptanceDueAt <= :now)
                or (b.status = :awaitingDepositStatus and b.depositDueAt <= :now))
            order by b.createdAt
            """)
    List<UUID> findExpirableIds(@Param("pendingStatus") BookingStatus pendingStatus,
                                @Param("awaitingDepositStatus") BookingStatus awaitingDepositStatus,
                                @Param("now") Instant now,
                                Pageable pageable);

    // Khóa tư vấn PostgreSQL trong transaction hiện tại (tự nhả khi commit/rollback): tuần tự hóa kiểm trùng lịch của
    // cùng caregiver / người bệnh giữa các request song song (bước kiểm ở service không tự chống race).
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:lockKey))) as schedule_lock", nativeQuery = true)
    Integer lockScheduleKey(@Param("lockKey") String lockKey);
}
