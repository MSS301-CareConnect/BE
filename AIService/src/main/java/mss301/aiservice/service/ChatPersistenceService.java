package mss301.aiservice.service;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.entity.ChatConversation;
import mss301.aiservice.entity.ChatMessage;
import mss301.aiservice.entity.ChatMessageMedia;

import java.util.List;
import java.util.UUID;

public interface ChatPersistenceService {
    // Lấy hội thoại hiện có hoặc tạo hội thoại mới.
    ChatConversation getOrCreateConversation(UUID userId, UUID conversationId, String prompt);

    // Lấy các tin nhắn gần nhất làm ngữ cảnh cho AI.
    List<ChatMessage> getRecentMessages(UUID userId, UUID conversationId);

    // Lưu câu hỏi, câu trả lời và ảnh đính kèm của một lượt chat.
    void saveExchange(UUID userId, UUID conversationId, String prompt, GroqResult groqResult,
                      List<ChatMediaUpload> uploadedImages);

    // Xóa hội thoại mới tạo khi lượt chat đầu tiên thất bại.
    void discardEmptyConversation(UUID userId, UUID conversationId);

    // Danh sách hội thoại của người dùng.
    List<ChatConversation> getConversations(UUID userId);

    // Lấy một hội thoại của người dùng.
    ChatConversation getConversation(UUID userId, UUID conversationId);

    // Lấy toàn bộ tin nhắn của hội thoại.
    List<ChatMessage> getMessages(UUID userId, UUID conversationId);

    // Lấy ảnh đính kèm của các tin nhắn.
    List<ChatMessageMedia> getMessageMedia(List<UUID> chatMessageIds);

    // Đổi tên hội thoại có kiểm tra version.
    ChatConversation updateTitle(UUID userId, UUID conversationId, String title, Long version);

    // Xóa hội thoại và trả về id các ảnh cần xóa trên MediaService.
    List<UUID> deleteConversation(UUID userId, UUID conversationId);
}
