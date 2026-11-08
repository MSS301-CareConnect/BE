package mss301.aiservice.service;

import jakarta.servlet.http.HttpServletRequest;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.ChatResponse;
import mss301.aiservice.dto.response.ConversationDetailResponse;
import mss301.aiservice.dto.response.ConversationSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ChatService {
    APIResponse<ChatResponse> chat(UUID userId, ChatPromptRequest request, List<MultipartFile> images,
                                   HttpServletRequest httpServletRequest);

    APIResponse<List<ConversationSummaryResponse>> getConversations(UUID userId);

    APIResponse<ConversationDetailResponse> getConversation(UUID userId, UUID conversationId);

    APIResponse<Void> deleteConversation(UUID userId, UUID conversationId);
}
