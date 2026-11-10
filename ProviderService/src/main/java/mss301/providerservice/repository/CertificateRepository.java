package mss301.providerservice.repository;

import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.CertificateVerificationStatus;
import mss301.providerservice.enums.CertificateAiCheckStatus;
import mss301.providerservice.enums.CertificateRiskLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.Instant;
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

    // Như CaregiverProfileRepository.findWithCenterById: kiểm chứng chỉ công khai ngoài transaction.
    @Query("select c from Certificate c join fetch c.caregiver g join fetch g.center where c.id = :id and c.deletedAt is null")
    Optional<Certificate> findWithCaregiverById(@Param("id") UUID id);
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

    // ─── Kiểm tra bằng AI (CertificateAiCheckScheduler) ─────────────────────────────────────────────────────────
    // Update có điều kiện, KHÔNG tăng version: chạy nền không được làm 409 người đang mở chứng chỉ; điều kiện
    // ai_check_id bảo đảm kết quả của nội dung cũ (caregiver đã sửa / admin chạy lại) không ghi đè lần mới.

    @Query("""
            select c.id from Certificate c
            where c.deletedAt is null and c.aiCheckStatus in :statuses and c.aiNextAttemptAt <= :now
            order by c.aiNextAttemptAt
            """)
    List<UUID> findDueAiCheckIds(@Param("statuses") Collection<CertificateAiCheckStatus> statuses,
                                 @Param("now") Instant now, Pageable pageable);

    // Nhận việc: QUEUED (hoặc PROCESSING quá lease) → PROCESSING đến leaseUntil. Trả 1 nếu lấy được.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Certificate c set c.aiCheckStatus = :processing, c.aiNextAttemptAt = :leaseUntil
            where c.id = :id and c.aiCheckId = :checkId and c.deletedAt is null
              and c.aiCheckStatus in :statuses and c.aiNextAttemptAt <= :now
            """)
    int claimAiCheck(@Param("id") UUID id, @Param("checkId") UUID checkId,
                     @Param("statuses") Collection<CertificateAiCheckStatus> statuses,
                     @Param("processing") CertificateAiCheckStatus processing, @Param("now") Instant now,
                     @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Certificate c set c.aiCheckStatus = :completed, c.aiRiskLevel = :riskLevel,
                c.aiWarnings = :warnings, c.aiExtracted = :extracted, c.aiModel = :model, c.aiCheckedAt = :checkedAt,
                c.aiCheckAttempts = c.aiCheckAttempts + 1, c.aiNextAttemptAt = null, c.aiLastError = null
            where c.id = :id and c.aiCheckId = :checkId and c.aiCheckStatus = :processing
            """)
    int completeAiCheck(@Param("id") UUID id, @Param("checkId") UUID checkId,
                        @Param("processing") CertificateAiCheckStatus processing,
                        @Param("completed") CertificateAiCheckStatus completed,
                        @Param("riskLevel") CertificateRiskLevel riskLevel, @Param("warnings") String warnings,
                        @Param("extracted") String extracted, @Param("model") String model,
                        @Param("checkedAt") Instant checkedAt);

    // Lỗi: thử lại sau (QUEUED + nextAttemptAt) hoặc dừng (FAILED, nextAttemptAt null).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Certificate c set c.aiCheckStatus = :status, c.aiCheckAttempts = :attempts,
                c.aiNextAttemptAt = :nextAttemptAt, c.aiLastError = :lastError
            where c.id = :id and c.aiCheckId = :checkId and c.aiCheckStatus = :processing
            """)
    int failAiCheck(@Param("id") UUID id, @Param("checkId") UUID checkId,
                    @Param("processing") CertificateAiCheckStatus processing,
                    @Param("status") CertificateAiCheckStatus status, @Param("attempts") int attempts,
                    @Param("nextAttemptAt") Instant nextAttemptAt, @Param("lastError") String lastError);

    // Số chứng chỉ chờ admin duyệt theo mức rủi ro AI (null = chưa có kết quả) và trạng thái kiểm tra.
    @Query("""
            select c.aiRiskLevel as riskLevel, c.aiCheckStatus as checkStatus, count(c) as total
            from Certificate c
            where c.deletedAt is null and c.verificationStatus = :status
              and (:centerId is null or c.caregiver.center.id = :centerId)
            group by c.aiRiskLevel, c.aiCheckStatus
            """)
    List<CertificateRiskCount> countPendingAuditByRisk(@Param("status") CertificateVerificationStatus status,
                                                       @Param("centerId") UUID centerId);
}
