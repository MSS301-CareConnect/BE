package mss301.aiservice.mapper;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.dto.response.*;
import mss301.aiservice.entity.ChatConversation;
import mss301.aiservice.entity.ChatMessage;
import mss301.aiservice.entity.ChatMessageMedia;
import mss301.aiservice.enums.MessageRole;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface ChatMapper {
    @Mapping(target = "conversationId", ignore = true)
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "conversationStatus", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ChatConversation toConversation(UUID userId, String title);

    @Mapping(target = "chatMessageId", ignore = true)
    @Mapping(target = "conversationId", source = "conversationId")
    @Mapping(target = "messageRole", source = "messageRole")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "sequenceNumber", source = "sequenceNumber")
    @Mapping(target = "modelName", source = "modelName")
    @Mapping(target = "promptTokens", source = "promptTokens")
    @Mapping(target = "completionTokens", source = "completionTokens")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ChatMessage toMessage(UUID conversationId, MessageRole messageRole, String content,
                          Integer sequenceNumber, String modelName, Integer promptTokens, Integer completionTokens);

    @Mapping(target = "conversationId", source = "conversationId")
    @Mapping(target = "conversationTitle", source = "conversationTitle")
    @Mapping(target = "answer", source = "answer")
    @Mapping(target = "remainingGuestPrompts", source = "remainingGuestPrompts")
    @Mapping(target = "createdAt", source = "createdAt")
    ChatResponse toChatResponse(UUID conversationId, String conversationTitle, String answer,
                                Integer remainingGuestPrompts, Instant createdAt);

    @Mapping(target = "chatMessageMediaId", ignore = true)
    @Mapping(target = "chatMessageId", source = "chatMessageId")
    @Mapping(target = "mediaFileId", source = "media.mediaFileId")
    @Mapping(target = "originalFileName", source = "media.originalFileName")
    @Mapping(target = "mimeType", source = "media.contentType")
    @Mapping(target = "sortOrder", source = "sortOrder")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ChatMessageMedia toMessageMedia(UUID chatMessageId, ChatMediaUpload media, Short sortOrder);

    // Người đăng nhập có dailyPromptLimit và remainingPrompts đều null: mặc định MapStruct khi đó trả null → mất result.
    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    ChatQuotaResponse toQuotaResponse(boolean guest, Integer dailyPromptLimit, Integer remainingPrompts,
                                      int maxPromptLength, boolean imagesAllowed, int maxImages, int maxImageSizeMb,
                                      int maxTotalImageSizeMb);

    ConversationSummaryResponse toConversationSummary(ChatConversation conversation);

    List<ConversationSummaryResponse> toConversationSummaries(List<ChatConversation> conversations);

    @Mapping(target = "images", ignore = true)
    ChatMessageResponse toMessageResponse(ChatMessage message);

    @Mapping(target = "mediaFileId", source = "media.mediaFileId")
    @Mapping(target = "originalFileName", source = "media.originalFileName")
    @Mapping(target = "mimeType", source = "media.mimeType")
    @Mapping(target = "downloadUrl", source = "downloadUrl")
    ChatImageResponse toImageResponse(ChatMessageMedia media, String downloadUrl);

    @Mapping(target = "conversationId", source = "conversation.conversationId")
    @Mapping(target = "title", source = "conversation.title")
    @Mapping(target = "messages", source = "messages")
    @Mapping(target = "createdAt", source = "conversation.createdAt")
    @Mapping(target = "updatedAt", source = "conversation.updatedAt")
    @Mapping(target = "version", source = "conversation.version")
    ConversationDetailResponse toConversationDetail(ChatConversation conversation, List<ChatMessageResponse> messages);
}
