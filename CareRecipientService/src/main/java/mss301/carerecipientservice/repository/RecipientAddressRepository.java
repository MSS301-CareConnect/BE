package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.RecipientAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientAddressRepository extends JpaRepository<RecipientAddress, UUID> {
    List<RecipientAddress> findByRecipientIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID recipientId);

    Optional<RecipientAddress> findByIdAndRecipientIdAndDeletedAtIsNull(UUID id, UUID recipientId);

    long countByRecipientIdAndDeletedAtIsNull(UUID recipientId);
}
