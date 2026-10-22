package mss301.providerservice.repository;

import jakarta.persistence.LockModeType;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
