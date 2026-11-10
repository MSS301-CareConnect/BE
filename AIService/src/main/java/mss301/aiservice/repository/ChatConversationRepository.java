package mss301.aiservice.repository;

import jakarta.persistence.LockModeType;
import mss301.aiservice.entity.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {
    Optional<ChatConversation> findByConversationIdAndUserIdAndDeletedAtIsNull(UUID conversationId, UUID userId);

    Page<ChatConversation> findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChatConversation> findByConversationIdAndDeletedAtIsNull(UUID conversationId);

    List<ChatConversation> findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);
}
