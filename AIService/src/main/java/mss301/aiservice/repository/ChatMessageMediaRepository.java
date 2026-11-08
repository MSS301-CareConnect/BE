package mss301.aiservice.repository;

import mss301.aiservice.entity.ChatMessageMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageMediaRepository extends JpaRepository<ChatMessageMedia, UUID> {
    List<ChatMessageMedia> findAllByChatMessageIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID chatMessageId);

    List<ChatMessageMedia> findAllByChatMessageIdInAndDeletedAtIsNullOrderBySortOrderAsc(Collection<UUID> chatMessageIds);
}
