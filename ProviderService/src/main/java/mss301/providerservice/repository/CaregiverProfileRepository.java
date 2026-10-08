package mss301.providerservice.repository;

import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CaregiverProfileRepository extends JpaRepository<CaregiverProfile, UUID> {
    Optional<CaregiverProfile> findByIdAndDeletedAtIsNull(UUID id);
    Optional<CaregiverProfile> findByUserIdAndDeletedAtIsNull(UUID userId);
    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);
    Page<CaregiverProfile> findByCenterIdAndDeletedAtIsNull(UUID centerId, Pageable pageable);
    Page<CaregiverProfile> findByCenterIdAndVerificationStatusAndDeletedAtIsNull(
            UUID centerId, VerificationStatus verificationStatus, Pageable pageable);
    Page<CaregiverProfile> findByCenterIdAndCaregiverStatusAndDeletedAtIsNull(
            UUID centerId, CaregiverStatus caregiverStatus, Pageable pageable);

    @Query("""
            select c from CaregiverProfile c
            where c.deletedAt is null
              and c.verificationStatus = mss301.providerservice.enums.VerificationStatus.VERIFIED
              and c.caregiverStatus = mss301.providerservice.enums.CaregiverStatus.ACTIVE
              and c.center.centerStatus = mss301.providerservice.enums.CenterStatus.ACTIVE
              and c.center.deletedAt is null
              and (:centerId is null or c.center.id = :centerId)
              and (:keyword is null or lower(c.fullName) like lower(concat('%', :keyword, '%')))
            """)
    Page<CaregiverProfile> catalog(@Param("centerId") UUID centerId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);
}
