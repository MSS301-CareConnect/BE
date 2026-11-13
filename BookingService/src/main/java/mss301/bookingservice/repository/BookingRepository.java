package mss301.bookingservice.repository;

import mss301.bookingservice.repository.projection.TopCaregiverView;
import mss301.bookingservice.repository.projection.CaregiverVisitCountView;
import mss301.bookingservice.repository.projection.CaregiverServiceCountView;
import mss301.bookingservice.repository.projection.CaregiverRatingView;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.repository.projection.StatisticsCountView;
import mss301.bookingservice.repository.projection.RatingCountView;
import mss301.bookingservice.repository.projection.OccurrenceStartView;
import mss301.bookingservice.repository.projection.FeedbackRatingView;
import mss301.bookingservice.repository.projection.BookingResponseTimeView;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingExpiredReason;
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

    // CR-3: đơn khách gửi thẳng cho caregiver (caregiver cấp booking), mới nhất trước.
    Page<Booking> findByCaregiverUserIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(UUID caregiverUserId,
                                                                                       BookingStatus status,
                                                                                       Pageable pageable);

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

    // Booking quá hạn xác nhận (trung tâm hoặc caregiver, CR-3), quá hạn phân caregiver hoặc quá hạn thanh toán khoản đầu
    // (scheduler xử lý theo lô). paymentCutoff = now − grace cọc: qua cả grace mới nhả slot (2026-11-09).
    @Query("""
            select b.id from Booking b
            where b.deletedAt is null
              and ((b.status in :confirmationStatuses and b.acceptanceDueAt <= :now)
                or (b.status = :assignmentStatus and b.assignmentDueAt <= :now)
                or (b.status = :paymentStatus and b.depositDueAt <= :paymentCutoff))
            order by b.createdAt
            """)
    List<UUID> findExpirableIds(@Param("confirmationStatuses") Collection<BookingStatus> confirmationStatuses,
                                @Param("assignmentStatus") BookingStatus assignmentStatus,
                                @Param("paymentStatus") BookingStatus paymentStatus,
                                @Param("now") Instant now,
                                @Param("paymentCutoff") Instant paymentCutoff,
                                Pageable pageable);

    // Booking chờ cọc đã qua hạn + grace (paymentCutoff = now − grace) — job nhả slot giữ chỗ (2026-11-09).
    @Query("""
            select b.id from Booking b
            where b.deletedAt is null and b.status = :status and b.depositDueAt <= :paymentCutoff
            order by b.depositDueAt
            """)
    List<UUID> findExpiredHoldIds(@Param("status") BookingStatus status, @Param("paymentCutoff") Instant paymentCutoff,
                                  Pageable pageable);

    // Booking đã trả khoản đầu, chưa trả đủ, chưa tới hạn phần còn lại — hạn gần nhất trước (nhắc 72h / 48h, M6).
    @Query("""
            select b.id from Booking b
            where b.deletedAt is null and b.status = :status and b.fullyPaidAt is null
              and b.remainingPaymentDueAt > :now
            order by b.remainingPaymentDueAt
            """)
    List<UUID> findRemainingPaymentReminderIds(@Param("status") BookingStatus status, @Param("now") Instant now,
                                               Pageable pageable);

    // Khóa tư vấn PostgreSQL trong transaction hiện tại (tự nhả khi commit/rollback): tuần tự hóa kiểm trùng lịch của
    // cùng caregiver / người bệnh giữa các request song song (bước kiểm ở service không tự chống race).
    @Query(value = "select 1 from (select pg_advisory_xact_lock(hashtext(:lockKey))) as schedule_lock", nativeQuery = true)
    Integer lockScheduleKey(@Param("lockKey") String lockKey);
    @Query("select b.status as category, count(b) as count from Booking b where b.deletedAt is null and b.createdAt >= :from and b.createdAt < :to and (:centerId is null or b.centerId = :centerId) group by b.status")
    List<StatisticsCountView> countBookings(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                            @Param("to") Instant to);

    // Hàng đợi trung tâm: số booking theo trạng thái (mọi thời điểm); centerId null = toàn hệ thống (admin).
    @Query("select b.status as category, count(b) as count from Booking b where b.deletedAt is null and (:centerId is null or b.centerId = :centerId) group by b.status")
    List<StatisticsCountView> countByStatus(@Param("centerId") UUID centerId);

    // SLA: trung tâm trả lời (xác nhận / từ chối) booking tạo trong kỳ — thời gian từ lúc tạo tới lúc trả lời.
    @Query("select h.booking.createdAt as createdAt, h.changedAt as changedAt from BookingStatusHistory h where h.fromStatus = :fromStatus and h.toStatus in :toStatuses and h.booking.createdAt >= :from and h.booking.createdAt < :to and (:centerId is null or h.booking.centerId = :centerId)")
    List<BookingResponseTimeView> findCenterResponses(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                                      @Param("to") Instant to, @Param("fromStatus") BookingStatus fromStatus,
                                                      @Param("toStatuses") Collection<BookingStatus> toStatuses);

    // SLA: booking tạo trong kỳ hết hạn vì trung tâm chưa xác nhận / chưa phân công kịp.
    @Query("select count(b) from Booking b where b.deletedAt is null and b.expiredReason in :reasons and b.createdAt >= :from and b.createdAt < :to and (:centerId is null or b.centerId = :centerId)")
    long countExpired(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to,
                      @Param("reasons") Collection<BookingExpiredReason> reasons);

    // Booking chờ trung tâm xác nhận / phân công, hạn trong cửa sổ nhắc lớn nhất (BookingExpiryScheduler).
    @Query("""
            select b.id from Booking b
            where b.deletedAt is null
              and ((b.status = :confirmationStatus and b.acceptanceDueAt > :now and b.acceptanceDueAt <= :windowEnd)
                or (b.status = :assignmentStatus and b.assignmentDueAt > :now and b.assignmentDueAt <= :windowEnd))
            order by b.createdAt
            """)
    List<UUID> findCenterReminderIds(@Param("confirmationStatus") BookingStatus confirmationStatus,
                                     @Param("assignmentStatus") BookingStatus assignmentStatus,
                                     @Param("now") Instant now, @Param("windowEnd") Instant windowEnd, Pageable pageable);

    @Query("select o.status as category, count(o) as count from BookingOccurrence o where o.deletedAt is null and o.startAt >= :from and o.startAt < :to and (:centerId is null or o.booking.centerId = :centerId) group by o.status")
    List<StatisticsCountView> countOccurrences(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                               @Param("to") Instant to);

    // Dashboard 2026-11-10: booking theo loại (SHIFT / RECURRING / CUSTOM) tạo trong kỳ.
    @Query("select b.bookingType as category, count(b) as count from Booking b where b.deletedAt is null and b.createdAt >= :from and b.createdAt < :to and (:centerId is null or b.centerId = :centerId) group by b.bookingType")
    List<StatisticsCountView> countBookingTypes(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                                @Param("to") Instant to);

    // Lúc tạo của từng booking trong kỳ — gom theo ngày / tháng (giờ VN) ở service, kỳ tối đa 366 ngày.
    @Query("select b.createdAt from Booking b where b.deletedAt is null and b.createdAt >= :from and b.createdAt < :to and (:centerId is null or b.centerId = :centerId)")
    List<Instant> findCreatedAts(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select o.startAt as startAt, o.status as status from BookingOccurrence o where o.deletedAt is null and o.startAt >= :from and o.startAt < :to and (:centerId is null or o.booking.centerId = :centerId)")
    List<OccurrenceStartView> findOccurrenceStarts(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                                   @Param("to") Instant to);

    @Query("select f.rating as rating, count(f) as count from Feedback f where f.deletedAt is null and f.visibility = :visibility and f.createdAt >= :from and f.createdAt < :to and (:centerId is null or f.centerId = :centerId) group by f.rating")
    List<RatingCountView> countRatings(@Param("centerId") UUID centerId, @Param("from") Instant from, @Param("to") Instant to,
                                       @Param("visibility") FeedbackVisibility visibility);

    @Query("select c.status as category, count(c) as count from Complaint c where c.deletedAt is null and c.createdAt >= :from and c.createdAt < :to and (:centerId is null or c.centerId = :centerId) group by c.status")
    List<StatisticsCountView> countComplaints(@Param("centerId") UUID centerId, @Param("from") Instant from,
                                              @Param("to") Instant to);

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

    // Số buổi của từng caregiver theo trạng thái (mọi thời điểm), lọc tùy chọn theo trung tâm và caregiver.
    @Query("""
            select o.caregiverId as caregiverId, o.status as status, count(o) as visits,
                sum(o.durationMinutes) as minutes, max(o.startAt) as lastStartAt
            from BookingOccurrence o where o.deletedAt is null and o.caregiverId is not null
              and (:centerId is null or o.booking.centerId = :centerId)
              and (:caregiverId is null or o.caregiverId = :caregiverId)
            group by o.caregiverId, o.status
            """)
    List<CaregiverVisitCountView> countCaregiverVisits(@Param("centerId") UUID centerId,
                                                       @Param("caregiverId") UUID caregiverId);

    // Buổi đã hoàn thành của từng caregiver theo dịch vụ. Buổi cũ không ghi careServiceId (booking một dịch vụ)
    // khớp mọi dịch vụ của booking; count distinct để một buổi không bị đếm trùng trong cùng dịch vụ.
    @Query("""
            select o.caregiverId as caregiverId, d.careServiceId as careServiceId, max(d.serviceName) as serviceName,
                count(distinct o.id) as completedVisits
            from BookingOccurrence o join BookingDetail d on d.booking = o.booking
            where o.deletedAt is null and o.status = :completed and o.caregiverId is not null
              and (o.careServiceId is null or d.careServiceId = o.careServiceId)
              and (:centerId is null or o.booking.centerId = :centerId)
              and (:caregiverId is null or o.caregiverId = :caregiverId)
            group by o.caregiverId, d.careServiceId
            order by count(distinct o.id) desc
            """)
    List<CaregiverServiceCountView> countCaregiverServices(@Param("centerId") UUID centerId,
                                                           @Param("caregiverId") UUID caregiverId,
                                                           @Param("completed") OccurrenceStatus completed);

    // Điểm trung bình và số đánh giá đang hiển thị của từng caregiver.
    @Query("""
            select f.caregiverId as caregiverId, avg(f.rating) as ratingAverage, count(f) as ratingCount
            from Feedback f where f.deletedAt is null and f.visibility = :visibility
              and (:centerId is null or f.centerId = :centerId)
              and (:caregiverId is null or f.caregiverId = :caregiverId)
            group by f.caregiverId
            """)
    List<CaregiverRatingView> summarizeCaregiverRatings(@Param("centerId") UUID centerId,
                                                        @Param("caregiverId") UUID caregiverId,
                                                        @Param("visibility") FeedbackVisibility visibility);

    @Query("""
            select distinct b.centerId from Booking b where b.deletedAt is null and (
                (b.createdAt >= :from and b.createdAt < :to)
                or exists (select o.id from BookingOccurrence o where o.booking = b and o.deletedAt is null and o.startAt >= :from and o.startAt < :to)
                or exists (select f.id from Feedback f where f.booking = b and f.deletedAt is null and f.createdAt >= :from and f.createdAt < :to)
                or exists (select c.id from Complaint c where c.bookingId = b.id and c.deletedAt is null and c.createdAt >= :from and c.createdAt < :to))
            order by b.centerId
            """)
    Page<UUID> findCenterIds(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    // M-07: booking chưa kết thúc của người bệnh.
    long countByRecipientIdAndStatusInAndDeletedAtIsNull(UUID recipientId, Collection<BookingStatus> statuses);
}
