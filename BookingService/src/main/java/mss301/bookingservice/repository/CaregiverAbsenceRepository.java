package mss301.bookingservice.repository;

import mss301.bookingservice.repository.projection.CaregiverAbsenceStatusCountView;
import jakarta.persistence.LockModeType;
import mss301.bookingservice.entity.CaregiverAbsence;
import mss301.bookingservice.enums.CaregiverAbsenceStatus;
import org.springframework.data.domain.Page;
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
public interface CaregiverAbsenceRepository extends JpaRepository<CaregiverAbsence, UUID> {

    boolean existsByOccurrenceIdAndDeletedAtIsNull(UUID occurrenceId);

    Optional<CaregiverAbsence> findByOccurrenceIdAndDeletedAtIsNull(UUID occurrenceId);

    @Query("""
            select a from CaregiverAbsence a join fetch a.occurrence o join fetch a.booking b
            where a.id = :id and a.deletedAt is null
            """)
    Optional<CaregiverAbsence> findWithOccurrence(@Param("id") UUID id);

    // Khóa dòng: caregiver gửi giải trình và trung tâm kết luận cùng lúc không ghi đè nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CaregiverAbsence a where a.id = :id and a.deletedAt is null")
    Optional<CaregiverAbsence> findForUpdate(@Param("id") UUID id);

    // Bộ lọc tùy chọn (null = không lọc); phạm vi theo role do service quyết (centerId / caregiverUserId). Khoảng thời gian
    // tính theo giờ bắt đầu của buổi bị vắng, buổi mới nhất trước.
    @Query(value = """
            select a from CaregiverAbsence a join fetch a.occurrence o join fetch a.booking b
            where a.deletedAt is null
              and (:centerId is null or a.centerId = :centerId)
              and (:caregiverUserId is null or a.caregiverUserId = :caregiverUserId)
              and (:caregiverId is null or a.caregiverId = :caregiverId)
              and (:bookingId is null or b.id = :bookingId)
              and (:status is null or a.status = :status)
              and o.startAt >= :from
              and o.startAt < :to
            order by o.startAt desc, a.id
            """, countQuery = """
            select count(a) from CaregiverAbsence a join a.occurrence o join a.booking b
            where a.deletedAt is null
              and (:centerId is null or a.centerId = :centerId)
              and (:caregiverUserId is null or a.caregiverUserId = :caregiverUserId)
              and (:caregiverId is null or a.caregiverId = :caregiverId)
              and (:bookingId is null or b.id = :bookingId)
              and (:status is null or a.status = :status)
              and o.startAt >= :from
              and o.startAt < :to
            """)
    Page<CaregiverAbsence> findPage(@Param("centerId") UUID centerId,
                                    @Param("caregiverUserId") UUID caregiverUserId,
                                    @Param("caregiverId") UUID caregiverId,
                                    @Param("bookingId") UUID bookingId,
                                    @Param("status") CaregiverAbsenceStatus status,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    Pageable pageable);

    @Query("""
            select a.status as status, count(a) as total
            from CaregiverAbsence a join a.occurrence o
            where a.deletedAt is null
              and (:centerId is null or a.centerId = :centerId)
              and (:caregiverUserId is null or a.caregiverUserId = :caregiverUserId)
              and (:caregiverId is null or a.caregiverId = :caregiverId)
              and o.startAt >= :from
              and o.startAt < :to
            group by a.status
            """)
    List<CaregiverAbsenceStatusCountView> countByStatus(@Param("centerId") UUID centerId,
                                                        @Param("caregiverUserId") UUID caregiverUserId,
                                                        @Param("caregiverId") UUID caregiverId,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);
}
