package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {
    // Liên hệ chính trước, rồi theo thứ tự ưu tiên.
    @Query("""
            select c from EmergencyContact c
            where c.recipient.id = :recipientId and c.deletedAt is null
            order by c.primary desc, c.priority asc, c.createdAt asc
            """)
    List<EmergencyContact> findAllByRecipient(@Param("recipientId") UUID recipientId);

    @Query("select c from EmergencyContact c where c.id = :id and c.recipient.id = :recipientId and c.deletedAt is null")
    Optional<EmergencyContact> findByIdAndRecipient(@Param("id") UUID id, @Param("recipientId") UUID recipientId);

    @Query("select c from EmergencyContact c where c.recipient.id = :recipientId and c.primary = true and c.deletedAt is null")
    Optional<EmergencyContact> findPrimary(@Param("recipientId") UUID recipientId);
}
