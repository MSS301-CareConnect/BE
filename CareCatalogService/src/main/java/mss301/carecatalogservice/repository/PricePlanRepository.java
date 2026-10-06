package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.PricePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PricePlanRepository extends JpaRepository<PricePlan, UUID> {
    Optional<PricePlan> findByIdAndDeletedAtIsNull(UUID id);
    
    List<PricePlan> findByCenterServiceIdAndDeletedAtIsNull(UUID centerServiceId);
}
