package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingOccurrenceRepository extends JpaRepository<BookingOccurrence, UUID> {

    @Query("select o from BookingOccurrence o where o.booking.id = :bookingId and o.deletedAt is null order by o.occurrenceNo")
    List<BookingOccurrence> findAllByBooking(@Param("bookingId") UUID bookingId);

    // BK-06: buổi của booking theo trang (lọc trạng thái).
    @Query(value = """
            select o from BookingOccurrence o
            where o.booking.id = :bookingId and o.deletedAt is null and o.status in :statuses
            order by o.occurrenceNo
            """, countQuery = """
            select count(o) from BookingOccurrence o
            where o.booking.id = :bookingId and o.deletedAt is null and o.status in :statuses
            """)
    Page<BookingOccurrence> findPageByBooking(@Param("bookingId") UUID bookingId,
                                              @Param("statuses") Collection<OccurrenceStatus> statuses,
                                              Pageable pageable);

    // BK-06: tổng số buổi / đã hoàn thành / buổi kế tiếp của mỗi booking trong trang danh sách — một truy vấn.
    @Query("""
            select o.booking.id as bookingId,
                   count(o) as totalCount,
                   sum(case when o.status = :completedStatus then 1 else 0 end) as completedCount,
                   min(case when o.status in :activeStatuses and o.endAt > :now then o.startAt end) as nextOccurrenceAt
            from BookingOccurrence o
            where o.booking.id in :bookingIds and o.deletedAt is null
            group by o.booking.id
            """)
    List<OccurrenceStatsView> summarizeByBookings(@Param("bookingIds") Collection<UUID> bookingIds,
                                                  @Param("activeStatuses") Collection<OccurrenceStatus> activeStatuses,
                                                  @Param("completedStatus") OccurrenceStatus completedStatus,
                                                  @Param("now") Instant now);

    @Query("select o from BookingOccurrence o join fetch o.booking b where o.id = :id and o.deletedAt is null and b.deletedAt is null")
    Optional<BookingOccurrence> findWithBooking(@Param("id") UUID id);

    @Query("""
            select o.id from BookingOccurrence o where o.deletedAt is null and o.reminderSentAt is null
              and o.caregiverUserId is not null and o.status in :statuses and o.booking.status in :bookingStatuses
              and o.startAt > :now and o.startAt <= :before order by o.startAt
            """)
    List<UUID> findShiftReminderIds(@Param("statuses") Collection<OccurrenceStatus> statuses,
            @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
            @Param("now") Instant now, @Param("before") Instant before, Pageable pageable);

    // Khóa dòng buổi khi đổi caregiver riêng cho buổi đó (hai manager cùng đổi một buổi).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from BookingOccurrence o where o.id = :id and o.deletedAt is null")
    Optional<BookingOccurrence> findForUpdate(@Param("id") UUID id);

    // Buổi còn hoạt động của caregiver giao với khoảng [rangeStart, rangeEnd) — service lọc giao chính xác từng slot.
    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where o.caregiverId = :caregiverId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt < :rangeEnd
              and o.endAt > :rangeStart
            """)
    List<BookingOccurrence> findCaregiverOverlaps(@Param("caregiverId") UUID caregiverId,
                                                  @Param("rangeStart") Instant rangeStart,
                                                  @Param("rangeEnd") Instant rangeEnd,
                                                  @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                  @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);

    // Buổi còn hoạt động của NHIỀU caregiver giao với [rangeStart, rangeEnd) — C5.1; service lọc giao chính xác từng slot.
    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where o.caregiverId in :caregiverIds
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt < :rangeEnd
              and o.endAt > :rangeStart
            """)
    List<BookingOccurrence> findCaregiversOverlaps(@Param("caregiverIds") Collection<UUID> caregiverIds,
                                                   @Param("rangeStart") Instant rangeStart,
                                                   @Param("rangeEnd") Instant rangeEnd,
                                                   @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                   @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);

    // Buổi còn hoạt động của người bệnh (mọi trung tâm) giao với khoảng [rangeStart, rangeEnd).
    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where b.recipientId = :recipientId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt < :rangeEnd
              and o.endAt > :rangeStart
            """)
    List<BookingOccurrence> findRecipientOverlaps(@Param("recipientId") UUID recipientId,
                                                  @Param("rangeStart") Instant rangeStart,
                                                  @Param("rangeEnd") Instant rangeEnd,
                                                  @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                  @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);

    // OPS-06: buổi chưa bắt đầu, quá hạn check-in, không có báo vắng, của booking đang chạy → job MISSED (theo lô).
    @Query("""
            select o.id from BookingOccurrence o join o.booking b
            where o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.noShowReportedAt is null
              and o.startAt <= :startedBefore
            order by o.startAt
            """)
    List<UUID> findMissedIds(@Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                             @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
                             @Param("startedBefore") Instant startedBefore,
                             Pageable pageable);

    // BK-04: buổi tương lai còn hoạt động của caregiver bị khóa, chưa đánh dấu cần đổi người.
    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where o.caregiverId = :caregiverId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt > :now
              and o.needsReassignment = false
            """)
    List<BookingOccurrence> findReassignmentCandidates(@Param("caregiverId") UUID caregiverId,
                                                       @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                       @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
                                                       @Param("now") Instant now);

    // OPS-06: báo vắng chưa được quyết định, buổi đã kết thúc trước endedBefore, chưa nhắc manager.
    @Query("""
            select o.id from BookingOccurrence o
            where o.deletedAt is null
              and o.status in :occurrenceStatuses
              and o.noShowReportedAt is not null
              and o.noShowDecidedAt is null
              and o.noShowRemindedAt is null
              and o.endAt <= :endedBefore
            order by o.endAt
            """)
    List<UUID> findUndecidedNoShowIds(@Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                      @Param("endedBefore") Instant endedBefore,
                                      Pageable pageable);

    // Lịch của caregiver (theo user_id Identity = sub JWT) bắt đầu trong [from, to), theo trang (BK-06).
    @Query(value = """
            select o from BookingOccurrence o join fetch o.booking b
            where o.caregiverUserId = :caregiverUserId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt >= :from
              and o.startAt < :to
            order by o.startAt, o.id
            """, countQuery = """
            select count(o) from BookingOccurrence o join o.booking b
            where o.caregiverUserId = :caregiverUserId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt >= :from
              and o.startAt < :to
            """)
    Page<BookingOccurrence> findCaregiverSchedule(@Param("caregiverUserId") UUID caregiverUserId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                  @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
                                                  Pageable pageable);

    // Quyền xem hồ sơ người bệnh tính THEO BUỔI (D5): caregiver (user_id Identity) đang được giao ít nhất một buổi còn
    // hoạt động, hoặc buổi COMPLETED kết thúc sau completedSince, của booking còn hiệu lực. Caregiver đã bị đổi khỏi mọi
    // buổi → không còn quyền. Cùng quy tắc với snapshot ở BookingOccurrenceServiceImpl.
    @Query("""
            select count(o) > 0 from BookingOccurrence o join o.booking b
            where o.caregiverUserId = :caregiverUserId
              and b.recipientId = :recipientId
              and o.deletedAt is null
              and b.deletedAt is null
              and b.status in :bookingStatuses
              and (o.status in :activeStatuses
                or (o.status = :completedStatus and o.endAt > :completedSince))
            """)
    boolean existsCaregiverAccess(@Param("caregiverUserId") UUID caregiverUserId,
                                  @Param("recipientId") UUID recipientId,
                                  @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses,
                                  @Param("activeStatuses") Collection<OccurrenceStatus> activeStatuses,
                                  @Param("completedStatus") OccurrenceStatus completedStatus,
                                  @Param("completedSince") Instant completedSince);
}
