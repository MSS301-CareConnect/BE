package mss301.providerservice.repository;

import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareCenterRepository extends JpaRepository<CareCenter, UUID>, JpaSpecificationExecutor<CareCenter> {

    // Trạng thái và outbox của trung tâm phải được ghi tuần tự.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CareCenter c where c.id = :id and c.deletedAt is null")
    Optional<CareCenter> findForUpdate(@Param("id") UUID id);

    Optional<CareCenter> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CareCenter> findByIdAndCenterStatusAndDeletedAtIsNull(UUID id, CenterStatus centerStatus);

    // Danh sách công khai (SRCH-02): keyword theo tên / địa chỉ — không theo email nội bộ.
    @Query("""
            select c from CareCenter c
            where c.deletedAt is null
              and c.centerStatus = :status
              and (:keywordPattern is null
                   or lower(c.centerName) like :keywordPattern
                   or lower(c.address) like :keywordPattern)
            """)
    Page<CareCenter> searchPublic(@Param("status") CenterStatus status,
                                  @Param("keywordPattern") String keywordPattern,
                                  Pageable pageable);

    // Như searchPublic nhưng giới hạn trong các trung tâm Catalog trả về khi lọc dịch vụ / giá.
    @Query("""
            select c from CareCenter c
            where c.deletedAt is null
              and c.centerStatus = :status
              and c.id in :centerIds
              and (:keywordPattern is null
                   or lower(c.centerName) like :keywordPattern
                   or lower(c.address) like :keywordPattern)
            """)
    Page<CareCenter> searchPublicByIds(@Param("status") CenterStatus status,
                                       @Param("centerIds") Collection<UUID> centerIds,
                                       @Param("keywordPattern") String keywordPattern,
                                       Pageable pageable);

    // Mỗi manager quản lý tối đa một trung tâm chưa xóa (uk_care_centers_manager_active).
    Optional<CareCenter> findByManagerUserIdAndDeletedAtIsNull(UUID managerUserId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, UUID id);

    boolean existsByManagerUserIdAndDeletedAtIsNull(UUID managerUserId);

    boolean existsByManagerUserIdAndIdNotAndDeletedAtIsNull(UUID managerUserId, UUID id);
    @Query("select c.centerStatus as category, count(c) as count from CareCenter c where c.deletedAt is null group by c.centerStatus")
    List<StatisticsCountView> countCentersByStatus();

    @Query("select c.caregiverStatus as category, count(c) as count from CaregiverProfile c where c.deletedAt is null group by c.caregiverStatus")
    List<StatisticsCountView> countCaregiversByStatus();

    @Query("select c.verificationStatus as category, count(c) as count from CaregiverProfile c where c.deletedAt is null group by c.verificationStatus")
    List<StatisticsCountView> countCaregiversByVerification();
}
