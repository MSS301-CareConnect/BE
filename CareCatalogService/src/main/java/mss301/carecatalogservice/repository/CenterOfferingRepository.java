package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.enums.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CenterOfferingRepository extends JpaRepository<CenterOffering, UUID> {
    Optional<CenterOffering> findByIdAndDeletedAtIsNull(UUID id);

    Page<CenterOffering> findByCenterIdAndDeletedAtIsNull(UUID centerId, Pageable pageable);

    Page<CenterOffering> findByCenterIdAndStatusAndDeletedAtIsNull(UUID centerId, ServiceStatus status, Pageable pageable);

    Optional<CenterOffering> findByIdAndCenterIdAndDeletedAtIsNull(UUID id, UUID centerId);

    Optional<CenterOffering> findByCenterIdAndCatalogItemIdAndDeletedAtIsNull(UUID centerId, UUID catalogItemId);

    boolean existsByCenterIdAndCatalogItemIdAndDeletedAtIsNull(UUID centerId, UUID catalogItemId);

    boolean existsByCatalogItemIdAndStatusAndDeletedAtIsNull(UUID catalogItemId, ServiceStatus status);

    // Dịch vụ khách đặt được: offering + danh mục chuẩn đang ở trạng thái bán và còn ít nhất một gói giá active.
    @Query(value = """
            select o from CenterOffering o join fetch o.catalogItem i
            where o.centerId = :centerId
              and o.deletedAt is null
              and i.deletedAt is null
              and o.status = :status
              and i.status = :status
              and exists (select p.id from PricePlan p
                          where p.centerOffering = o and p.isActive = true and p.deletedAt is null)
            """,
            countQuery = """
            select count(o) from CenterOffering o join o.catalogItem i
            where o.centerId = :centerId
              and o.deletedAt is null
              and i.deletedAt is null
              and o.status = :status
              and i.status = :status
              and exists (select p.id from PricePlan p
                          where p.centerOffering = o and p.isActive = true and p.deletedAt is null)
            """)
    Page<CenterOffering> findSellableByCenter(@Param("centerId") UUID centerId,
                                              @Param("status") ServiceStatus status,
                                              Pageable pageable);
}
