package mss301.providerservice.repository;

import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.CertificateVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID>, JpaSpecificationExecutor<Certificate> {
    // Duyệt và sửa chứng chỉ phải tuần tự để không duyệt nội dung cũ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Certificate c where c.id = :id and c.deletedAt is null")
    Optional<Certificate> findForUpdate(@Param("id") UUID id);

    Optional<Certificate> findByIdAndDeletedAtIsNull(UUID id);
    List<Certificate> findByCaregiverIdAndDeletedAtIsNullOrderByIssuedDateDesc(UUID caregiverId);
    List<Certificate> findByCaregiverIdAndVerificationStatusAndDeletedAtIsNullOrderByIssuedDateDesc(
            UUID caregiverId, CertificateVerificationStatus verificationStatus);

    // Một query gom nhóm cho cả trang kết quả tìm kiếm thay vì một count mỗi caregiver.
    @Query("""
            select c.caregiver.id as caregiverId, count(c) as certificateCount
            from Certificate c
            where c.deletedAt is null
              and c.verificationStatus = :status
              and c.caregiver.id in :caregiverIds
              and (c.expiryDate is null or c.expiryDate >= :today)
            group by c.caregiver.id
            """)
    List<CaregiverCertificateCount> countByCaregiverIds(@Param("caregiverIds") Collection<UUID> caregiverIds,
                                                        @Param("status") CertificateVerificationStatus status, @Param("today") LocalDate today);
}
