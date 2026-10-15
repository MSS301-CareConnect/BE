package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
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

    @Query("select o from BookingOccurrence o join fetch o.booking b where o.id = :id and o.deletedAt is null and b.deletedAt is null")
    Optional<BookingOccurrence> findWithBooking(@Param("id") UUID id);

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

    // Lịch của caregiver (theo user_id Identity = sub JWT) bắt đầu trong [from, to).
    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where o.caregiverUserId = :caregiverUserId
              and o.deletedAt is null
              and b.deletedAt is null
              and o.status in :occurrenceStatuses
              and b.status in :bookingStatuses
              and o.startAt >= :from
              and o.startAt < :to
            order by o.startAt
            """)
    List<BookingOccurrence> findCaregiverSchedule(@Param("caregiverUserId") UUID caregiverUserId,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("occurrenceStatuses") Collection<OccurrenceStatus> occurrenceStatuses,
                                                  @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);

    @Query("""
            select o from BookingOccurrence o join fetch o.booking b
            where o.id = :id
              and o.caregiverUserId = :caregiverUserId
              and o.deletedAt is null
              and b.deletedAt is null
              and b.status in :bookingStatuses
            """)
    Optional<BookingOccurrence> findForCaregiver(@Param("id") UUID id,
                                                 @Param("caregiverUserId") UUID caregiverUserId,
                                                 @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);
}
