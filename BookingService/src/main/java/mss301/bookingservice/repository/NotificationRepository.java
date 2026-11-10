package mss301.bookingservice.repository;

import mss301.bookingservice.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    Optional<Notification> findByIdAndRecipientUserIdAndDeletedAtIsNull(UUID id, UUID recipientUserId);
    long countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(UUID recipientUserId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null and n.deletedAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    // Đánh dấu đã đọc / chưa đọc các thông báo được chọn — chỉ của chính người dùng, chỉ dòng đổi trạng thái.
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.id in :ids and n.readAt is null and n.deletedAt is null")
    int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = null where n.recipientUserId = :userId and n.id in :ids and n.readAt is not null and n.deletedAt is null")
    int markUnread(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    // Xóa mềm các thông báo được chọn — chỉ của chính người dùng.
    @Modifying
    @Query("update Notification n set n.deletedAt = :now where n.recipientUserId = :userId and n.id in :ids and n.deletedAt is null")
    int softDelete(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.deletedAt = :now where n.recipientUserId = :userId and n.readAt is not null and n.deletedAt is null")
    int softDeleteRead(@Param("userId") UUID userId, @Param("now") Instant now);

    // Đích điều hướng theo loại ref (một truy vấn mỗi loại cho cả trang thông báo).
    @Query("select o.id as refId, o.booking.id as bookingId, o.id as occurrenceId from BookingOccurrence o where o.id in :ids")
    List<NotificationLinkView> findOccurrenceLinks(@Param("ids") Collection<UUID> ids);

    @Query("select s.id as refId, s.booking.id as bookingId, s.occurrence.id as occurrenceId from CareSession s where s.id in :ids")
    List<NotificationLinkView> findSessionLinks(@Param("ids") Collection<UUID> ids);

    @Query("select r.id as refId, r.bookingId as bookingId, r.occurrenceId as occurrenceId from CareReport r where r.id in :ids")
    List<NotificationLinkView> findReportLinks(@Param("ids") Collection<UUID> ids);

    @Query("select c.id as refId, c.bookingId as bookingId, c.occurrenceId as occurrenceId from Complaint c where c.id in :ids")
    List<NotificationLinkView> findComplaintLinks(@Param("ids") Collection<UUID> ids);

    @Query("select f.id as refId, f.booking.id as bookingId from Feedback f where f.id in :ids")
    List<NotificationBookingLinkView> findFeedbackLinks(@Param("ids") Collection<UUID> ids);
}
