package mss301.mediaservice.repository;

import mss301.mediaservice.entity.MediaFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    Optional<MediaFile> findByIdAndDeletedAtIsNull(UUID id);
}
