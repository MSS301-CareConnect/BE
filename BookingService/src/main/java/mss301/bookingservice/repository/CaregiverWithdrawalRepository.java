package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CaregiverWithdrawal;
import mss301.bookingservice.enums.OccurrenceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CaregiverWithdrawalRepository extends JpaRepository<CaregiverWithdrawal, UUID> {

    boolean existsByOccurrenceIdAndCaregiverIdAndDeletedAtIsNull(UUID occurrenceId, UUID caregiverId);

    // Caregiver đã xin hủy các buổi này — tự phân công không chọn lại họ.
    @Query("select distinct w.caregiverId from CaregiverWithdrawal w where w.deletedAt is null and w.occurrence.id in :occurrenceIds")
    List<UUID> findCaregiverIdsByOccurrenceIds(@Param("occurrenceIds") Collection<UUID> occurrenceIds);

    // Hàng đợi của trung tâm. pendingOnly = buổi chưa bắt đầu và vẫn thuộc caregiver xin hủy (chưa phân người khác);
    // bookingId null = mọi booking. Buổi gần nhất trước.
    @Query(value = """
            select w from CaregiverWithdrawal w join fetch w.occurrence o join fetch w.booking b
            where w.deletedAt is null and w.centerId = :centerId
              and (:bookingId is null or b.id = :bookingId)
              and (:pendingOnly = false
                   or (o.caregiverId = w.caregiverId and o.status in :notStarted and o.startAt > :now))
            order by o.startAt
            """, countQuery = """
            select count(w) from CaregiverWithdrawal w join w.occurrence o join w.booking b
            where w.deletedAt is null and w.centerId = :centerId
              and (:bookingId is null or b.id = :bookingId)
              and (:pendingOnly = false
                   or (o.caregiverId = w.caregiverId and o.status in :notStarted and o.startAt > :now))
            """)
    Page<CaregiverWithdrawal> findCenterWithdrawals(@Param("centerId") UUID centerId,
                                                    @Param("bookingId") UUID bookingId,
                                                    @Param("pendingOnly") boolean pendingOnly,
                                                    @Param("notStarted") Collection<OccurrenceStatus> notStarted,
                                                    @Param("now") Instant now,
                                                    Pageable pageable);

    // Yêu cầu của chính caregiver (theo user id trong token), mới nhất trước.
    @Query(value = """
            select w from CaregiverWithdrawal w join fetch w.occurrence o join fetch w.booking b
            where w.deletedAt is null and w.caregiverUserId = :caregiverUserId
              and (:bookingId is null or b.id = :bookingId)
            order by w.createdAt desc
            """, countQuery = """
            select count(w) from CaregiverWithdrawal w join w.booking b
            where w.deletedAt is null and w.caregiverUserId = :caregiverUserId
              and (:bookingId is null or b.id = :bookingId)
            """)
    Page<CaregiverWithdrawal> findCaregiverWithdrawals(@Param("caregiverUserId") UUID caregiverUserId,
                                                       @Param("bookingId") UUID bookingId,
                                                       Pageable pageable);
}
