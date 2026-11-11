package mss301.aiservice.mapper;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.dto.response.ChatImageResponse;
import mss301.aiservice.dto.response.ChatMessageResponse;
import mss301.aiservice.dto.response.ChatQuotaResponse;
import mss301.aiservice.dto.response.ChatResponse;
import mss301.aiservice.dto.response.ConversationDetailResponse;
import mss301.aiservice.dto.response.ConversationSummaryResponse;
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
    @Mapping(target = "conversationStatus", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ChatConversation toConversation(UUID userId, String title);

    @Mapping(target = "chatMessageId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    ChatMessage toMessage(UUID conversationId, MessageRole messageRole, String content,
                          Integer sequenceNumber, String modelName, Integer promptTokens, Integer completionTokens);

    ChatResponse toChatResponse(UUID conversationId, String conversationTitle, String answer,
                                Integer remainingGuestPrompts, Instant createdAt);

    @Mapping(target = "chatMessageMediaId", ignore = true)
    @Mapping(target = "mimeType", source = "media.contentType")
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

    ChatImageResponse toImageResponse(ChatMessageMedia media, String downloadUrl);

    ConversationDetailResponse toConversationDetail(ChatConversation conversation, List<ChatMessageResponse> messages);
}
