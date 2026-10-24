package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.util.LockHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import mss301.bookingservice.enums.FeedbackVisibility;
import mss301.bookingservice.enums.CareSessionStatus;
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
    @Query("select b.status as category, count(b) as count from Booking b where b.deletedAt is null and b.createdAt >= :from and b.createdAt < :to and (:centerId is null or b.centerId = :centerId) group by b.status")
    List<StatisticsCountView> countBookings(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.status as category, count(o) as count from BookingOccurrence o where o.deletedAt is null and o.startAt >= :from and o.startAt < :to and (:centerId is null or o.booking.centerId = :centerId) group by o.status")
    List<StatisticsCountView> countOccurrences(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select c.status as category, count(c) as count from Complaint c where c.deletedAt is null and c.createdAt >= :from and c.createdAt < :to and (:centerId is null or c.centerId = :centerId) group by c.status")
    List<StatisticsCountView> countComplaints(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select avg(f.rating) as ratingAverage, count(f) as ratingCount from Feedback f where f.deletedAt is null and f.visibility = :visibility and f.createdAt >= :from and f.createdAt < :to and (:centerId is null or f.centerId = :centerId)")
    FeedbackRatingView summarizeRating(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to,
                                        @Param("visibility") FeedbackVisibility visibility);

    @Query("""
            select s.caregiverId as caregiverId, count(s) as completedSessions,
                (select avg(f.rating) from Feedback f where f.caregiverId = s.caregiverId and f.deletedAt is null
                 and f.visibility = :visibility and f.createdAt >= :from and f.createdAt < :to) as avgRating
            from CareSession s where s.deletedAt is null and s.status = :ended
              and s.occurrence.startAt >= :from and s.occurrence.startAt < :to
              and (:centerId is null or s.booking.centerId = :centerId)
            group by s.caregiverId order by count(s) desc, s.caregiverId
            """)
    List<TopCaregiverView> topCaregivers(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to,
                                        @Param("visibility") FeedbackVisibility visibility, @Param("ended") CareSessionStatus ended,
                                        Pageable pageable);

    @Query("""
            select distinct b.centerId from Booking b where b.deletedAt is null and (
                (b.createdAt >= :from and b.createdAt < :to)
                or exists (select o.id from BookingOccurrence o where o.booking = b and o.deletedAt is null and o.startAt >= :from and o.startAt < :to)
                or exists (select f.id from Feedback f where f.booking = b and f.deletedAt is null and f.createdAt >= :from and f.createdAt < :to)
                or exists (select c.id from Complaint c where c.bookingId = b.id and c.deletedAt is null and c.createdAt >= :from and c.createdAt < :to))
            order by b.centerId
            """)
    Page<UUID> findCenterIds(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
