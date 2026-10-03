package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, UUID> {
    @Query("""
            select m from Medication m
            where m.recipient.id = :recipientId and m.deletedAt is null
            order by m.active desc, m.startDate desc, m.createdAt asc
            """)
    List<Medication> findAllByRecipient(@Param("recipientId") UUID recipientId);

    @Query("""
            select m from Medication m
            where m.recipient.id = :recipientId and m.active = :active and m.deletedAt is null
            order by m.startDate desc, m.createdAt asc
            """)
    List<Medication> findAllByRecipientAndActive(@Param("recipientId") UUID recipientId, @Param("active") boolean active);

    @Query("select m from Medication m where m.id = :id and m.recipient.id = :recipientId and m.deletedAt is null")
    Optional<Medication> findByIdAndRecipient(@Param("id") UUID id, @Param("recipientId") UUID recipientId);
}
