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
    // Xem lượt chat còn lại.
    APIResponse<ChatQuotaResponse> getQuota(UUID userId, HttpServletRequest httpServletRequest);

    // Danh sách hội thoại.
    APIResponse<List<ConversationSummaryResponse>> list(UUID userId);

    // Chi tiết hội thoại kèm tin nhắn.
    APIResponse<ConversationDetailResponse> get(UUID userId, UUID conversationId);

    // Gửi câu hỏi và nhận câu trả lời từ AI.
    APIResponse<ChatResponse> chat(UUID userId, ChatPromptRequest request, List<MultipartFile> images,
                                   HttpServletRequest httpServletRequest);

    // Đổi tên hội thoại.
    APIResponse<ConversationSummaryResponse> update(UUID userId, UUID conversationId, ConversationUpdateRequest request);

    // Xóa hội thoại.
    APIResponse<Void> delete(UUID userId, UUID conversationId);
}
