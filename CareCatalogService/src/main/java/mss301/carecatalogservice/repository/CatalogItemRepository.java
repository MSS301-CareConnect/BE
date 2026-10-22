package mss301.carecatalogservice.repository;

import mss301.carecatalogservice.entity.CatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID>, JpaSpecificationExecutor<CatalogItem> {
    Optional<CatalogItem> findByIdAndDeletedAtIsNull(UUID id);
}
