package mss301.bookingservice.repository;

import jakarta.persistence.LockModeType;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.enums.CareSessionStatus;
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
public interface CareSessionRepository extends JpaRepository<CareSession, UUID> {

    @Query("""
            select s from CareSession s join fetch s.occurrence o join fetch s.booking b
            where s.id = :id and s.deletedAt is null
            """)
    Optional<CareSession> findWithBooking(@Param("id") UUID id);

    // Khóa session khi kết thúc ca: check-out của caregiver và buộc kết thúc của manager không cùng chạy.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CareSession s where s.id = :id and s.deletedAt is null")
    Optional<CareSession> findForUpdate(@Param("id") UUID id);

    boolean existsByOccurrenceIdAndDeletedAtIsNull(UUID occurrenceId);

    Optional<CareSession> findByOccurrenceIdAndDeletedAtIsNull(UUID occurrenceId);

    @Query("select s.occurrence.id from CareSession s where s.booking.id = :bookingId and s.deletedAt is null")
    List<UUID> findOccurrenceIdsByBooking(@Param("bookingId") UUID bookingId);

    // Session còn mở dù buổi đã kết thúc quá lâu và chưa nhắc lần nào (job nhắc OPS-04).
    @Query("""
            select s.id from CareSession s join s.occurrence o
            where s.status = :status
              and s.overdueRemindedAt is null
              and s.deletedAt is null
              and o.endAt <= :endedBefore
            order by o.endAt
            """)
    List<UUID> findOverdueIds(@Param("status") CareSessionStatus status,
                              @Param("endedBefore") Instant endedBefore,
                              Pageable pageable);
}
