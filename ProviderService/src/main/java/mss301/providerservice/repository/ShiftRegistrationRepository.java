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

import java.util.Optional;
import java.util.UUID;

public interface ShiftRegistrationRepository extends JpaRepository<ShiftRegistration, UUID> {
    boolean existsByWorkShiftIdAndCaregiverId(UUID workShiftId, UUID caregiverId);
    long countByWorkShiftIdAndRegistrationStatus(UUID workShiftId, RegistrationStatus registrationStatus);
    Optional<ShiftRegistration> findByIdAndDeletedAtIsNull(UUID id);
    Page<ShiftRegistration> findByWorkShiftIdAndDeletedAtIsNull(UUID workShiftId, Pageable pageable);
    Page<ShiftRegistration> findByCaregiverIdAndDeletedAtIsNull(UUID caregiverId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ShiftRegistration r where r.id = :id and r.deletedAt is null")
    Optional<ShiftRegistration> findByIdForUpdate(@Param("id") UUID id);
}
