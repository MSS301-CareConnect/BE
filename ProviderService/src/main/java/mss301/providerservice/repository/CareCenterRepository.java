package mss301.providerservice.repository;

import mss301.providerservice.entity.CareCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareCenterRepository extends JpaRepository<CareCenter, UUID>, JpaSpecificationExecutor<CareCenter> {

    Optional<CareCenter> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, UUID id);

    boolean existsByManagerUserIdAndDeletedAtIsNull(UUID managerUserId);

    boolean existsByManagerUserIdAndIdNotAndDeletedAtIsNull(UUID managerUserId, UUID id);
}
