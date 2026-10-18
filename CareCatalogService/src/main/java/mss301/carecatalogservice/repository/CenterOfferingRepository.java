package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.CenterOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CenterOfferingRepository extends JpaRepository<CenterOffering, UUID> {
    Optional<CenterOffering> findByIdAndDeletedAtIsNull(UUID id);

    List<CenterOffering> findByCenterIdAndDeletedAtIsNull(UUID centerId);

    Optional<CenterOffering> findByIdAndCenterIdAndDeletedAtIsNull(UUID id, UUID centerId);

    Optional<CenterOffering> findByCenterIdAndCatalogItemIdAndDeletedAtIsNull(UUID centerId, UUID catalogItemId);

    boolean existsByCenterIdAndCatalogItemIdAndDeletedAtIsNull(UUID centerId, UUID catalogItemId);
}
