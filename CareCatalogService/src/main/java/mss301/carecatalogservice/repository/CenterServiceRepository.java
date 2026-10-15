package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.CenterService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CenterServiceRepository extends JpaRepository<CenterService, UUID> {
    Optional<CenterService> findByIdAndDeletedAtIsNull(UUID id);
    
    List<CenterService> findByCenterIdAndDeletedAtIsNull(UUID centerId);
    
    Optional<CenterService> findByIdAndCenterIdAndDeletedAtIsNull(UUID id, UUID centerId);

    Optional<CenterService> findByCenterIdAndCareServiceIdAndDeletedAtIsNull(UUID centerId, UUID careServiceId);

    boolean existsByCenterIdAndCareServiceIdAndDeletedAtIsNull(UUID centerId, UUID careServiceId);
}
