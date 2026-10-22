package mss301.providerservice.repository;

import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByIdAndDeletedAtIsNull(UUID id);
    List<Certificate> findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(UUID caregiverId);
    List<Certificate> findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
            UUID caregiverId, VerificationStatus verificationStatus);

    // Một query gom nhóm cho cả trang kết quả tìm kiếm thay vì một count mỗi caregiver.
    @Query("""
            select c.caregiver.id as caregiverId, count(c) as certificateCount
            from Certificate c
            where c.deletedAt is null
              and c.verificationStatus = :status
              and c.caregiver.id in :caregiverIds
            group by c.caregiver.id
            """)
    List<CaregiverCertificateCount> countByCaregiverIds(@Param("caregiverIds") Collection<UUID> caregiverIds,
                                                        @Param("status") VerificationStatus status);
}
