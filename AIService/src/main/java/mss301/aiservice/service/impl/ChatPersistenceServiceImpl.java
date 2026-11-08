package mss301.aiservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.entity.ChatConversation;
import mss301.aiservice.entity.ChatMessage;
import mss301.aiservice.entity.ChatMessageMedia;
import mss301.aiservice.enums.ConversationStatus;
import mss301.aiservice.enums.MessageRole;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.mapper.ChatMapper;
import mss301.aiservice.repository.ChatConversationRepository;
import mss301.aiservice.repository.ChatMessageMediaRepository;
import mss301.aiservice.repository.ChatMessageRepository;
import mss301.aiservice.service.ChatPersistenceService;
import mss301.aiservice.service.ConversationTitleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatPersistenceServiceImpl implements ChatPersistenceService {
    ChatConversationRepository chatConversationRepository;
    ChatMessageRepository chatMessageRepository;
    ChatMessageMediaRepository chatMessageMediaRepository;
    ConversationTitleService conversationTitleService;
    ChatMapper chatMapper;
    AIProperties aiProperties;
    Clock clock;

    @Override
    @Transactional
    public ChatConversation getOrCreateConversation(UUID userId, UUID conversationId, String prompt) {
        if (conversationId == null) {
            String title = conversationTitleService.generateTitle(prompt);
            ChatConversation conversation = chatMapper.toConversation(userId, title);
            return chatConversationRepository.saveAndFlush(conversation);
        }

        ChatConversation conversation = findConversationForUpdate(conversationId);
        checkOwner(conversation, userId);
        checkActive(conversation);

        return conversation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentMessages(UUID userId, UUID conversationId) {
        ChatConversation conversation = chatConversationRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                        .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        checkActive(conversation);

        int historyLimit = aiProperties.getChat().getMaxHistoryMessages();

        List<ChatMessage> descendingMessages = chatMessageRepository.findAllByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(
                                conversationId, PageRequest.of(0, historyLimit));

        List<ChatMessage> ascendingMessages = new ArrayList<>(descendingMessages);
        Collections.reverse(ascendingMessages);

        return ascendingMessages;
    }

    @Override
    @Transactional
    public void saveExchange(UUID userId, UUID conversationId, String prompt, GroqResult groqResult, List<ChatMediaUpload> uploadedImages) {
        ChatConversation conversation = findConversationForUpdate(conversationId);
        checkOwner(conversation, userId);
        checkActive(conversation);

        int userSequenceNumber = findNextSequenceNumber(conversationId);

        ChatMessage userMessage = chatMapper.toMessage(
                        conversationId,
                        MessageRole.USER,
                        prompt,
                        userSequenceNumber,
                        null,
                        null,
                        null
                );

        ChatMessage savedUserMessage = chatMessageRepository.saveAndFlush(userMessage);
        saveMessageMedia(savedUserMessage.getChatMessageId(), uploadedImages);

        ChatMessage assistantMessage = chatMapper.toMessage(
                        conversationId,
                        MessageRole.ASSISTANT,
                        groqResult.getAnswer(),
                        userSequenceNumber + 1,
                        groqResult.getModel(),
                        groqResult.getPromptTokens(),
                        groqResult.getCompletionTokens()
                );

        chatMessageRepository.save(assistantMessage);
        conversation.setUpdatedAt(clock.instant());
        chatConversationRepository.save(conversation);
    }

    @Override
    @Transactional
    public void discardEmptyConversation(UUID userId, UUID conversationId) {
        ChatConversation conversation = chatConversationRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                        .orElse(null);

        if (conversation == null) {
            return;
        }

        boolean hasMessages = chatMessageRepository.existsByConversationIdAndDeletedAtIsNull(conversationId);

        if (hasMessages) {
            return;
        }

        conversation.setDeletedAt(clock.instant());
        chatConversationRepository.save(conversation);

    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatConversation> getConversations(UUID userId) {
        return chatConversationRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatConversation getConversation(UUID userId, UUID conversationId) {
        return chatConversationRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    @Override
    public List<ChatMessage> getMessages(UUID userId, UUID conversationId) {
        getConversation(userId, conversationId);
        return chatMessageRepository.findAllByConversationIdAndDeletedAtIsNullOrderBySequenceNumberAsc(conversationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageMedia> getMessageMedia(List<UUID> chatMessageIds) {
        if (chatMessageIds == null || chatMessageIds.isEmpty()) {
            return List.of();
        }

        return chatMessageMediaRepository.findAllByChatMessageIdInAndDeletedAtIsNullOrderBySortOrderAsc(chatMessageIds);
    }

    @Override
    @Transactional
    public List<UUID> deleteConversation(UUID userId, UUID conversationId) {
        ChatConversation conversation = chatConversationRepository.findByConversationIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                        .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        List<ChatMessage> messages = chatMessageRepository.findAllByConversationIdAndDeletedAtIsNullOrderBySequenceNumberAsc(conversationId);

        List<UUID> messageIds = messages.stream()
                        .map(ChatMessage::getChatMessageId)
                        .toList();

        List<ChatMessageMedia> mediaFiles = messageIds.isEmpty()
                        ? List.of()
                        : chatMessageMediaRepository
                        .findAllByChatMessageIdInAndDeletedAtIsNullOrderBySortOrderAsc(messageIds);

        Instant now = clock.instant();

        for (ChatMessageMedia media : mediaFiles) {
            media.setDeletedAt(now);
        }

        for (ChatMessage message : messages) {
            message.setDeletedAt(now);
        }

        conversation.setDeletedAt(now);
        chatMessageMediaRepository.saveAll(mediaFiles);
        chatMessageRepository.saveAll(messages);
        chatConversationRepository.save(conversation);

        return mediaFiles.stream()
                .map(ChatMessageMedia::getMediaFileId)
                .toList();
    }

    private ChatConversation findConversationForUpdate(UUID conversationId) {
        return chatConversationRepository.findByConversationIdAndDeletedAtIsNull(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));
    }

    private void checkOwner(ChatConversation conversation, UUID userId) {
        if (!userId.equals(conversation.getUserId())) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }
    }

    private void checkActive(ChatConversation conversation) {
        if (conversation.getConversationStatus() != ConversationStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONVERSATION_ARCHIVED);
        }
    }
    private int findNextSequenceNumber(UUID conversationId
    ) {
        return chatMessageRepository.findTopByConversationIdAndDeletedAtIsNullOrderBySequenceNumberDesc(conversationId)
                .map(ChatMessage::getSequenceNumber)
                .map(number -> number + 1)
                .orElse(1);
    }

    private void saveMessageMedia(UUID chatMessageId, List<ChatMediaUpload> uploadedImages) {
        if (uploadedImages == null || uploadedImages.isEmpty()) {
            return;
        }

        List<ChatMessageMedia> messageMedia = new ArrayList<>();

        for (int index = 0; index < uploadedImages.size(); index++) {
            ChatMessageMedia media = chatMapper.toMessageMedia(chatMessageId, uploadedImages.get(index), (short) index);
            messageMedia.add(media);
        }

        chatMessageMediaRepository.saveAll(messageMedia);
    }
}
