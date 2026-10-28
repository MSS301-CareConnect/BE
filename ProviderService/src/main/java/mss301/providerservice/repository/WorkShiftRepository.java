package mss301.providerservice.repository;

import jakarta.persistence.LockModeType;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkShiftRepository extends JpaRepository<WorkShift, UUID> {
    Optional<WorkShift> findByIdAndDeletedAtIsNull(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkShift s where s.id = :id and s.deletedAt is null")
    Optional<WorkShift> findByIdForUpdate(@Param("id") UUID id);

    // excludedStatus: caregiver không thấy ca DRAFT (null = không loại trạng thái nào).
    @Query("""
            select s from WorkShift s
            where s.deletedAt is null
              and (:centerId is null or s.center.id = :centerId)
              and (:status is null or s.shiftStatus = :status)
              and (:excludedStatus is null or s.shiftStatus <> :excludedStatus)
              and (:fromDate is null or s.workDate >= :fromDate)
              and (:toDate is null or s.workDate <= :toDate)
            """)
    Page<WorkShift> search(@Param("centerId") UUID centerId,
                           @Param("status") ShiftStatus status,
                           @Param("excludedStatus") ShiftStatus excludedStatus,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate,
                           Pageable pageable);
}
