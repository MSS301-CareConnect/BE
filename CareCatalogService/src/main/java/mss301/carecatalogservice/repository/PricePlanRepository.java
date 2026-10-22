package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricePlanRepository extends JpaRepository<PricePlan, UUID> {
    Optional<PricePlan> findByIdAndDeletedAtIsNull(UUID id);

    List<PricePlan> findByCenterOfferingIdAndDeletedAtIsNull(UUID centerOfferingId);

    Optional<PricePlan> findByIdAndCenterOfferingIdAndDeletedAtIsNull(UUID id, UUID centerOfferingId);

    List<PricePlan> findByCenterOfferingIdInAndDeletedAtIsNull(Collection<UUID> centerOfferingIds);

    List<PricePlan> findByCenterOfferingIdInAndIsActiveTrueAndDeletedAtIsNull(Collection<UUID> centerOfferingIds);

    boolean existsByCenterOfferingIdAndIsActiveTrueAndDeletedAtIsNull(UUID centerOfferingId);

    // Dịch vụ chuẩn còn gói giá đang áp dụng ở bất kỳ trung tâm nào → không cho xóa mềm (CAT-02).
    @Query("""
            select count(p) > 0 from PricePlan p join p.centerOffering o
            where o.catalogItem.id = :catalogItemId
              and p.isActive = true
              and p.deletedAt is null
              and o.deletedAt is null
            """)
    boolean existsActiveByCatalogItem(@Param("catalogItemId") UUID catalogItemId);

    // Gói giá đang bán (gói active, offering + danh mục chuẩn ở trạng thái bán) của mọi trung tâm, cho price-summary.
    @Query("""
            select o.centerId as centerId, p.basePrice as basePrice
            from PricePlan p join p.centerOffering o join o.catalogItem i
            where p.isActive = true
              and p.deletedAt is null
              and o.deletedAt is null
              and i.deletedAt is null
              and o.status = :status
              and i.status = :status
            """)
    List<CenterPriceView> findSellablePrices(@Param("status") ServiceStatus status);

    // Như trên nhưng chỉ cho một dịch vụ chuẩn — tách method thay vì tham số nullable (PostgreSQL không suy được kiểu).
    @Query("""
            select o.centerId as centerId, p.basePrice as basePrice
            from PricePlan p join p.centerOffering o join o.catalogItem i
            where i.id = :catalogItemId
              and p.isActive = true
              and p.deletedAt is null
              and o.deletedAt is null
              and i.deletedAt is null
              and o.status = :status
              and i.status = :status
            """)
    List<CenterPriceView> findSellablePricesByCatalogItem(@Param("catalogItemId") UUID catalogItemId,
                                                          @Param("status") ServiceStatus status);
}
