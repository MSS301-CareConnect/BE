package mss301.carerecipientservice.repository;

import jakarta.persistence.LockModeType;
import mss301.carerecipientservice.entity.ManagerInvitation;
import mss301.carerecipientservice.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManagerInvitationRepository extends JpaRepository<ManagerInvitation, UUID> {

    @Query("select i from ManagerInvitation i where i.recipient.id = :recipientId order by i.createdAt desc")
    List<ManagerInvitation> findAllByRecipient(@Param("recipientId") UUID recipientId);

    @Query("""
            select count(i) > 0 from ManagerInvitation i
            where i.recipient.id = :recipientId and i.email = :email and i.status = :status and i.expiresAt > :now
            """)
    boolean existsOpen(@Param("recipientId") UUID recipientId, @Param("email") String email,
                       @Param("status") InvitationStatus status, @Param("now") Instant now);

    // Lời mời còn hiệu lực gửi tới người dùng; hồ sơ đã lưu trữ / xóa không hiện.
    @Query("""
            select i from ManagerInvitation i join fetch i.recipient r
            where i.inviteeUserId = :userId and i.status = :status and i.expiresAt > :now and r.deletedAt is null
            order by i.createdAt desc
            """)
    List<ManagerInvitation> findOpenForInvitee(@Param("userId") UUID userId, @Param("status") InvitationStatus status,
                                               @Param("now") Instant now);

    // Khóa lời mời khi chấp nhận / từ chối / thu hồi: hai thao tác song song không cùng đổi trạng thái.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ManagerInvitation i join fetch i.recipient where i.id = :id")
    Optional<ManagerInvitation> findForUpdate(@Param("id") UUID id);
}
