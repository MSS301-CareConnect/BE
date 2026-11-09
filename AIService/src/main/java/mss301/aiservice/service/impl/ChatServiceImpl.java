package mss301.aiservice.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.client.GroqClient;
import mss301.aiservice.client.GroqMessageFactory;
import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.dto.request.ConversationUpdateRequest;
import mss301.aiservice.dto.response.*;
import mss301.aiservice.entity.ChatConversation;
import mss301.aiservice.entity.ChatMessage;
import mss301.aiservice.entity.ChatMessageMedia;
import mss301.aiservice.mapper.ChatMapper;
import mss301.aiservice.service.ChatMediaService;
import mss301.aiservice.service.ChatPersistenceService;
import mss301.aiservice.service.ChatService;
import mss301.aiservice.service.ChatValidationService;
import mss301.aiservice.service.GuestUsageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatServiceImpl implements ChatService {
    ChatValidationService chatValidationService;
    GuestUsageService guestUsageService;
    ChatPersistenceService chatPersistenceService;
    ChatMediaService chatMediaService;
    GroqMessageFactory groqMessageFactory;
    GroqClient groqClient;
    ChatMapper chatMapper;
    AIProperties aiProperties;
    Clock clock;


    @Override
    public APIResponse<ChatResponse> chat(UUID userId, ChatPromptRequest request, List<MultipartFile> images, HttpServletRequest httpServletRequest) {
        String prompt = chatValidationService.validateAndNormalize(userId, request, images);

        if (userId == null) {
            return chatGuest(prompt, httpServletRequest);
        }
        return chatAuthenticated(userId, request, prompt, images);
    }

    @Override
    public APIResponse<ChatQuotaResponse> getQuota(UUID userId, HttpServletRequest httpServletRequest) {
        AIProperties.Chat chat = aiProperties.getChat();
        boolean guest = userId == null;

        // Khách: số lượt còn lại trong ngày, không gửi ảnh. Người đăng nhập (mọi role): không giới hạn lượt, tối đa N ảnh.
        Integer dailyPromptLimit = guest ? chat.getGuestDailyPromptLimit() : null;
        Integer remainingPrompts = guest ? guestUsageService.getRemainingPrompts(httpServletRequest) : null;
        int maxPromptLength = guest ? chat.getGuestMaxPromptLength() : chat.getCustomerMaxPromptLength();
        int maxImages = guest ? 0 : chat.getMaxImages();

        ChatQuotaResponse response = chatMapper.toQuotaResponse(guest, dailyPromptLimit, remainingPrompts,
                maxPromptLength, !guest, maxImages, chat.getMaxImageSizeMb(), chat.getMaxTotalImageSizeMb());

        return APIResponse.success("Lấy hạn mức trợ lý AI thành công", response);
    }

    @Override
    public APIResponse<List<ConversationSummaryResponse>> list(UUID userId) {
        List<ChatConversation> conversations = chatPersistenceService.getConversations(userId);
        List<ConversationSummaryResponse> responses = chatMapper.toConversationSummaries(conversations);

        return APIResponse.success("Lấy danh sách cuộc trò chuyện thành công", responses);
    }

    @Override
    public APIResponse<ConversationDetailResponse> get(UUID userId, UUID conversationId) {
        ChatConversation conversation = chatPersistenceService.getConversation(userId, conversationId);

        List<ChatMessage> messages = chatPersistenceService.getMessages(userId, conversationId);

        List<UUID> messageIds = messages.stream()
                        .map(ChatMessage::getChatMessageId)
                        .toList();

        List<ChatMessageMedia> allMedia = chatPersistenceService.getMessageMedia(messageIds);

        Map<UUID, List<ChatMessageMedia>> mediaByMessage = new HashMap<>();

        for (ChatMessageMedia media : allMedia) {
            mediaByMessage.computeIfAbsent(media.getChatMessageId(), ignored -> new ArrayList<>()).add(media);
        }

        List<ChatMessageResponse> messageResponses = new ArrayList<>();

        for (ChatMessage message : messages) {
            ChatMessageResponse messageResponse = chatMapper.toMessageResponse(message);

            List<ChatImageResponse> imageResponses = new ArrayList<>();

            List<ChatMessageMedia> messageMedia = mediaByMessage.getOrDefault(
                            message.getChatMessageId(),
                            List.of());

            for (ChatMessageMedia media : messageMedia) {
                String downloadUrl = chatMediaService.getDownloadUrl(media.getMediaFileId());
                imageResponses.add(chatMapper.toImageResponse(media, downloadUrl));
            }

            messageResponse.setImages(imageResponses);
            messageResponses.add(messageResponse);
        }

        ConversationDetailResponse response = chatMapper.toConversationDetail(conversation, messageResponses);

        return APIResponse.success("Lấy lịch sử cuộc trò chuyện thành công", response);
    }

    @Override
    public APIResponse<ConversationSummaryResponse> update(UUID userId, UUID conversationId,
                                                          ConversationUpdateRequest request) {
        ChatConversation conversation = chatPersistenceService.updateTitle(userId, conversationId,
                request.getTitle(), request.getVersion());
        ConversationSummaryResponse response = chatMapper.toConversationSummary(conversation);

        return APIResponse.success("Đổi tên cuộc trò chuyện thành công", response);
    }

    @Override
    public APIResponse<Void> delete(UUID userId, UUID conversationId) {
        List<UUID> mediaFileIds = chatPersistenceService.deleteConversation(userId, conversationId);
        chatMediaService.deleteQuietly(mediaFileIds);

        return APIResponse.success("Xóa cuộc trò chuyện thành công");
    }

    private APIResponse<ChatResponse> chatGuest(String prompt, HttpServletRequest httpServletRequest) {
        boolean promptConsumed = false;

        try {
            int remainingPrompts = guestUsageService.consumePrompt(httpServletRequest);
            promptConsumed = true;
            List<GroqMessageRequest> messages = groqMessageFactory.buildMessages(List.of(), prompt, List.of());
            GroqResult groqResult = groqClient.generateAnswer(messages);

            ChatResponse response = chatMapper.toChatResponse(
                            null,
                            null,
                            groqResult.getAnswer(),
                            remainingPrompts,
                            clock.instant());

            return APIResponse.success("Trợ lý AI đã trả lời", response);

        } catch (RuntimeException exception) {
            if (promptConsumed) {
                guestUsageService.restorePrompt(httpServletRequest);
            }

            throw exception;
        }
    }

    private APIResponse<ChatResponse> chatAuthenticated(UUID userId, ChatPromptRequest request,
                                                        String prompt, List<MultipartFile> images) {

        boolean newConversation = request.getConversationId() == null;
        ChatConversation conversation = chatPersistenceService.getOrCreateConversation(userId, request.getConversationId(), prompt);

        List<ChatMediaUpload> uploadedImages = List.of();

        try {
            List<ChatMessage> history = chatPersistenceService
                            .getRecentMessages(userId, conversation.getConversationId());

            uploadedImages = chatMediaService.uploadImages(userId, conversation.getConversationId(), images);

            List<GroqMessageRequest> messages = groqMessageFactory
                            .buildMessages(history, prompt, uploadedImages);

            GroqResult groqResult = groqClient.generateAnswer(messages);

            chatPersistenceService.saveExchange(
                    userId,
                    conversation.getConversationId(),
                    prompt,
                    groqResult,
                    uploadedImages
            );

            ChatResponse response = chatMapper.toChatResponse(
                            conversation.getConversationId(),
                            newConversation
                                    ? conversation.getTitle()
                                    : null,
                            groqResult.getAnswer(),
                            null,
                            clock.instant()
                    );

            return APIResponse.success("Trợ lý AI đã trả lời", response);

        } catch (RuntimeException exception) {
            deleteUploadedImages(uploadedImages);

            if (newConversation) {
                chatPersistenceService.discardEmptyConversation(userId, conversation.getConversationId());
            }

            throw exception;
        }
    }

    private void deleteUploadedImages(List<ChatMediaUpload> uploadedImages) {
        if (uploadedImages == null || uploadedImages.isEmpty()) {
            return;
        }

        List<UUID> mediaFileIds = uploadedImages
                        .stream()
                        .map(ChatMediaUpload::getMediaFileId)
                        .toList();

        chatMediaService.deleteQuietly(mediaFileIds);
    }
}
