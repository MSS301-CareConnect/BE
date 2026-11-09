package mss301.aiservice.service;

import jakarta.servlet.http.HttpServletRequest;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.dto.request.ConversationUpdateRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.ChatQuotaResponse;
import mss301.aiservice.dto.response.ChatResponse;
import mss301.aiservice.dto.response.ConversationDetailResponse;
import mss301.aiservice.dto.response.ConversationSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ChatService {
    APIResponse<ChatQuotaResponse> getQuota(UUID userId, HttpServletRequest httpServletRequest);

    APIResponse<List<ConversationSummaryResponse>> list(UUID userId);

    APIResponse<ConversationDetailResponse> get(UUID userId, UUID conversationId);

    APIResponse<ChatResponse> chat(UUID userId, ChatPromptRequest request, List<MultipartFile> images,
                                   HttpServletRequest httpServletRequest);

    APIResponse<ConversationSummaryResponse> update(UUID userId, UUID conversationId, ConversationUpdateRequest request);

    APIResponse<Void> delete(UUID userId, UUID conversationId);
}
