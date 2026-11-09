package mss301.aiservice.service;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.entity.ChatConversation;
import mss301.aiservice.entity.ChatMessage;
import mss301.aiservice.entity.ChatMessageMedia;

import java.util.List;
import java.util.UUID;

public interface ChatPersistenceService {
    ChatConversation getOrCreateConversation(UUID userId, UUID conversationId, String prompt);

    List<ChatMessage> getRecentMessages(UUID userId, UUID conversationId);

    void saveExchange(UUID userId, UUID conversationId, String prompt, GroqResult groqResult,
                      List<ChatMediaUpload> uploadedImages);

    void discardEmptyConversation(UUID userId, UUID conversationId);

    List<ChatConversation> getConversations(UUID userId);

    ChatConversation getConversation(UUID userId, UUID conversationId);

    List<ChatMessage> getMessages(UUID userId, UUID conversationId);

    List<ChatMessageMedia> getMessageMedia(List<UUID> chatMessageIds);

    ChatConversation updateTitle(UUID userId, UUID conversationId, String title, Long version);

    List<UUID> deleteConversation(UUID userId, UUID conversationId);
}
