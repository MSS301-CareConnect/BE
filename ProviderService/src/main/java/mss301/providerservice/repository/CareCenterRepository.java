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

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareCenterRepository extends JpaRepository<CareCenter, UUID>, JpaSpecificationExecutor<CareCenter> {

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
}
