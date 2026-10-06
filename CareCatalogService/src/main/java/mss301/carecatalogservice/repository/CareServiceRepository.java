package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.CareService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareServiceRepository extends JpaRepository<CareService, UUID> {
    Optional<CareService> findByIdAndDeletedAtIsNull(UUID id);

    List<CareService> findAllByDeletedAtIsNull();
}
