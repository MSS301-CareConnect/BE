package mss301.providerservice.repository;

import mss301.providerservice.entity.ProviderMedia;
import mss301.providerservice.enums.MediaOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderMediaRepository extends JpaRepository<ProviderMedia, UUID> {
    List<ProviderMedia> findAllByOwnerTypeAndOwnerIdOrderByDisplayOrderAsc(MediaOwnerType ownerType, UUID ownerId);

    Optional<ProviderMedia> findByOwnerTypeAndOwnerIdAndMediaFileId(MediaOwnerType ownerType, UUID ownerId, UUID mediaFileId);

    @Query("""
        select coalesce(max(media.displayOrder), -1)
        from ProviderMedia media
        where media.ownerType = :ownerType
          and media.ownerId = :ownerId
        """)
    int findMaximumDisplayOrder(@Param("ownerType") MediaOwnerType ownerType, @Param("ownerId") UUID ownerId);
}
