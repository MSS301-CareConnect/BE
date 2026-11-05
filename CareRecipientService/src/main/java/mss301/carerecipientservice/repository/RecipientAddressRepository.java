package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.RecipientAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientAddressRepository extends JpaRepository<RecipientAddress, UUID> {
    @Query("""
            select a from RecipientAddress a
            where a.recipient.id = :recipientId and a.deletedAt is null
            order by a.createdAt asc
            """)
    List<RecipientAddress> findAllByRecipient(@Param("recipientId") UUID recipientId);

    @Query("select a from RecipientAddress a where a.id = :id and a.recipient.id = :recipientId and a.deletedAt is null")
    Optional<RecipientAddress> findByIdAndRecipient(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Query("select count(a) from RecipientAddress a where a.recipient.id = :recipientId and a.deletedAt is null")
    long countByRecipient(@Param("recipientId") UUID recipientId);
}
