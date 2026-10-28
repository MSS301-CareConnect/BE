package mss301.providerservice.repository;

import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.enums.ScheduleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaregiverScheduleRepository extends JpaRepository<CaregiverSchedule, UUID> {
    Optional<CaregiverSchedule> findByIdAndDeletedAtIsNull(UUID id);
    Optional<CaregiverSchedule> findByRegistrationId(UUID registrationId);
    Page<CaregiverSchedule> findByCaregiverIdAndDeletedAtIsNull(UUID caregiverId, Pageable pageable);
    Page<CaregiverSchedule> findByWorkShiftCenterIdAndDeletedAtIsNull(UUID centerId, Pageable pageable);

    @Query("""
            select count(cs) > 0 from CaregiverSchedule cs
            join cs.workShift ws
            where cs.deletedAt is null
              and cs.caregiver.id = :caregiverId
              and cs.scheduleStatus in :statuses
              and ws.workDate = :workDate
              and ws.startTime < :endTime
              and ws.endTime > :startTime
            """)
    boolean hasOverlap(@Param("caregiverId") UUID caregiverId,
                       @Param("workDate") LocalDate workDate,
                       @Param("startTime") LocalTime startTime,
                       @Param("endTime") LocalTime endTime,
                       @Param("statuses") Collection<ScheduleStatus> statuses);

    // Lịch rảnh (PRV-02) của nhiều caregiver theo ngày làm việc (giờ VN); fetch ca để quy đổi giờ không thêm query.
    @Query("""
            select cs from CaregiverSchedule cs
            join fetch cs.workShift ws
            where cs.deletedAt is null
              and cs.caregiver.id in :caregiverIds
              and cs.scheduleStatus = :status
              and ws.workDate between :fromDate and :toDate
            """)
    List<CaregiverSchedule> findByCaregiversAndWorkDateRange(@Param("caregiverIds") Collection<UUID> caregiverIds,
                                                             @Param("status") ScheduleStatus status,
                                                             @Param("fromDate") LocalDate fromDate,
                                                             @Param("toDate") LocalDate toDate);
}
