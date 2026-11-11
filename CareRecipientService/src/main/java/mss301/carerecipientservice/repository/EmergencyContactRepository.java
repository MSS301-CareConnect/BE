package mss301.carerecipientservice.repository;

import mss301.carerecipientservice.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {
    // Liên hệ chính trước, rồi theo thứ tự ưu tiên.
    List<EmergencyContact> findByRecipientIdAndDeletedAtIsNullOrderByPrimaryDescPriorityAscCreatedAtAsc(UUID recipientId);

    Optional<EmergencyContact> findByIdAndRecipientIdAndDeletedAtIsNull(UUID id, UUID recipientId);

    Optional<EmergencyContact> findByRecipientIdAndPrimaryTrueAndDeletedAtIsNull(UUID recipientId);
}
