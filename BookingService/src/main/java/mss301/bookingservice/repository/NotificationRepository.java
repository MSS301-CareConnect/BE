package mss301.bookingservice.repository;

import mss301.bookingservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByIdAndRecipientUserIdAndDeletedAtIsNull(UUID id, UUID recipientUserId);
    long countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID recipientUserId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null and n.deletedAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
