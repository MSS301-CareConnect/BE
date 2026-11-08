package mss301.aiservice.repository;

import mss301.aiservice.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findAllByConversationIdAndDeletedAtIsNullOrderBySequenceNumberAsc(UUID conversationId);

    List<ChatMessage> findAllByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(UUID conversationId, Pageable pageable);

    Optional<ChatMessage> findTopByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(UUID conversationId);

    boolean existsByConversationIdAndDeletedAtIsNull(UUID conversationId);
}
