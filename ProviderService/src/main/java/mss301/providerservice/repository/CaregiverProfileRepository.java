package mss301.providerservice.repository;

import jakarta.persistence.LockModeType;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import mss301.providerservice.enums.Gender;
import mss301.providerservice.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaregiverProfileRepository extends JpaRepository<CaregiverProfile, UUID>, JpaSpecificationExecutor<CaregiverProfile> {
    Optional<CaregiverProfile> findByIdAndDeletedAtIsNull(UUID id);
    Optional<CaregiverProfile> findByUserIdAndDeletedAtIsNull(UUID userId);

    // Khóa hồ sơ khi thay toàn bộ kỹ năng: hai PUT song song không cùng xóa/thêm dòng caregiver_services.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaregiverProfile c where c.id = :id and c.deletedAt is null")
    Optional<CaregiverProfile> findForUpdate(@Param("id") UUID id);

    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);
    Page<CaregiverProfile> findByCenterIdAndDeletedAtIsNull(UUID centerId, Pageable pageable);
    Page<CaregiverProfile> findByCenterIdAndVerificationStatusAndDeletedAtIsNull(
            UUID centerId, VerificationStatus verificationStatus, Pageable pageable);
    Page<CaregiverProfile> findByCenterIdAndCaregiverStatusAndDeletedAtIsNull(
            UUID centerId, CaregiverStatus caregiverStatus, Pageable pageable);

    @Query("""
            select c from CaregiverProfile c
            where c.deletedAt is null
              and c.verificationStatus = :verificationStatus
              and c.caregiverStatus = :caregiverStatus
              and c.center.centerStatus = :centerStatus
              and c.center.deletedAt is null
              and (:centerId is null or c.center.id = :centerId)
              and (:keyword is null or lower(c.fullName) like lower(concat('%', :keyword, '%')))
            """)
    Page<CaregiverProfile> catalog(@Param("verificationStatus") VerificationStatus verificationStatus,
                                   @Param("caregiverStatus") CaregiverStatus caregiverStatus,
                                   @Param("centerStatus") CenterStatus centerStatus,
                                   @Param("centerId") UUID centerId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    /**
     * Bước (a) của tìm kiếm (SRCH-01): mọi caregiver thỏa điều kiện tĩnh của một trung tâm, làm được TẤT CẢ dịch vụ
     * yêu cầu (giao tập: đếm số dịch vụ chuẩn khác nhau khớp = :serviceCount). Không phân trang ở DB vì còn lọc lịch
     * sau đó — bên gọi giới hạn số dòng bằng Pageable (500 + 1 để phát hiện quá rộng).
     */
    @Query("""
            select c from CaregiverProfile c
            where c.deletedAt is null
              and c.center.id = :centerId
              and c.verificationStatus = :verificationStatus
              and c.caregiverStatus = :caregiverStatus
              and c.id in (
                  select s.id.caregiverId from CaregiverSkill s
                  where s.careServiceId in :careServiceIds
                  group by s.id.caregiverId
                  having count(distinct s.careServiceId) = :serviceCount)
              and (:gender is null or c.gender = :gender)
              and (:minExperienceYears is null or c.experienceYears >= :minExperienceYears)
              and (:minRating is null or c.ratingAverage >= :minRating)
              and (:keywordPattern is null or lower(c.fullName) like :keywordPattern)
            """)
    List<CaregiverProfile> searchCandidates(@Param("centerId") UUID centerId,
                                            @Param("verificationStatus") VerificationStatus verificationStatus,
                                            @Param("caregiverStatus") CaregiverStatus caregiverStatus,
                                            @Param("careServiceIds") Collection<UUID> careServiceIds,
                                            @Param("serviceCount") long serviceCount,
                                            @Param("gender") Gender gender,
                                            @Param("minExperienceYears") Integer minExperienceYears,
                                            @Param("minRating") BigDecimal minRating,
                                            @Param("keywordPattern") String keywordPattern,
                                            Pageable pageable);

    List<CaregiverProfile> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
