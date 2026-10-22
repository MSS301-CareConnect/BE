package mss301.identityservice.repository;

import mss301.identityservice.entity.ServiceClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceClientRepository extends JpaRepository<ServiceClient, UUID> {
    Optional<ServiceClient> findByClientIdAndDeletedAtIsNull(String clientId);

    boolean existsByIdAndClientIdAndActiveTrueAndDeletedAtIsNull(UUID id, String clientId);
}
