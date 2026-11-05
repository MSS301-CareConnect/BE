package mss301.providerservice.repository;

import jakarta.persistence.LockModeType;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRegistrationRepository extends JpaRepository<ShiftRegistration, UUID> {
    // Chỉ đăng ký còn hiệu lực (PENDING/APPROVED) chặn đăng ký lại — đã CANCELLED/REJECTED thì được đăng ký lại.
    boolean existsByWorkShiftIdAndCaregiverIdAndRegistrationStatusIn(UUID workShiftId, UUID caregiverId,
                                                                     Collection<RegistrationStatus> registrationStatuses);

    long countByWorkShiftIdAndRegistrationStatus(UUID workShiftId, RegistrationStatus registrationStatus);

    Optional<ShiftRegistration> findByIdAndDeletedAtIsNull(UUID id);

    Page<ShiftRegistration> findByWorkShiftIdAndDeletedAtIsNull(UUID workShiftId, Pageable pageable);

    Page<ShiftRegistration> findByCaregiverIdAndDeletedAtIsNull(UUID caregiverId, Pageable pageable);

    @Query("""
            select r.workShift.id as shiftId, count(r) as approvedCount
            from ShiftRegistration r
            where r.workShift.id in :shiftIds
              and r.registrationStatus = :status
            group by r.workShift.id
            """)
    List<ShiftApprovedCount> countByShiftIds(@Param("shiftIds") Collection<UUID> shiftIds,
                                             @Param("status") RegistrationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ShiftRegistration r where r.id = :id and r.deletedAt is null")
    Optional<ShiftRegistration> findByIdForUpdate(@Param("id") UUID id);

    // Danh sách có lọc trạng thái (null = mọi trạng thái); fetch ca + caregiver để map tóm tắt ca không N+1.
    @EntityGraph(attributePaths = {"workShift", "caregiver"})
    @Query("""
            select r from ShiftRegistration r
            where r.deletedAt is null
              and r.workShift.id = :shiftId
              and (:status is null or r.registrationStatus = :status)
            """)
    Page<ShiftRegistration> findByShift(@Param("shiftId") UUID shiftId,
                                        @Param("status") RegistrationStatus status,
                                        Pageable pageable);

    @EntityGraph(attributePaths = {"workShift", "caregiver"})
    @Query("""
            select r from ShiftRegistration r
            where r.deletedAt is null
              and r.caregiver.id = :caregiverId
              and (:status is null or r.registrationStatus = :status)
            """)
    Page<ShiftRegistration> findByCaregiver(@Param("caregiverId") UUID caregiverId,
                                            @Param("status") RegistrationStatus status,
                                            Pageable pageable);

    List<ShiftRegistration> findByWorkShiftIdAndRegistrationStatusInAndDeletedAtIsNull(
            UUID workShiftId, Collection<RegistrationStatus> registrationStatuses);

    // Job vòng đời: đăng ký còn chờ khi ca đã bắt đầu thì không còn duyệt được → từ chối kèm lý do hệ thống.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ShiftRegistration r
            set r.registrationStatus = mss301.providerservice.enums.RegistrationStatus.REJECTED, r.rejectionReason = :reason, r.reviewedAt = :now,
                r.version = r.version + 1, r.updatedAt = :now
            where r.deletedAt is null
              and r.registrationStatus = mss301.providerservice.enums.RegistrationStatus.PENDING
              and r.workShift.id in (select s.id from WorkShift s where (s.workDate < :today or (s.workDate = :today and s.startTime <= :nowTime)))
            """)
    int rejectPendingOfStartedShifts(@Param("today") LocalDate today, @Param("nowTime") LocalTime nowTime,
                                     @Param("now") Instant now, @Param("reason") String reason);
}
