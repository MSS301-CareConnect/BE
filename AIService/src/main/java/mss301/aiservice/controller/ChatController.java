package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.dto.request.ConversationUpdateRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.ChatQuotaResponse;
import mss301.aiservice.dto.response.ChatResponse;
import mss301.aiservice.dto.response.ConversationDetailResponse;
import mss301.aiservice.dto.response.ConversationSummaryResponse;
import mss301.aiservice.security.CurrentUserId;
import mss301.aiservice.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "AI Chat", description = "Chatbot CareConnect")
public class ChatController {
    ChatService chatService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    // API xem số lượt chat còn lại của người dùng hoặc khách.
    @GetMapping("/chat/quota")
    @SecurityRequirements({})
    public APIResponse<ChatQuotaResponse> getQuota(@CurrentUserId(required = false) UUID userId,
                                                   HttpServletRequest httpServletRequest) {
        return chatService.getQuota(userId, httpServletRequest);
    }

    // API danh sách hội thoại của người dùng.
    @GetMapping("/conversations")
    public APIResponse<List<ConversationSummaryResponse>> list(@CurrentUserId UUID userId) {
        return chatService.list(userId);
    }

    // API xem chi tiết một hội thoại kèm tin nhắn.
    @GetMapping("/conversations/{conversationId}")
    public APIResponse<ConversationDetailResponse> get(@CurrentUserId UUID userId,
                                                       @PathVariable UUID conversationId) {
        return chatService.get(userId, conversationId);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    // API gửi câu hỏi (kèm ảnh) và nhận câu trả lời từ AI.
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirements({})
    public APIResponse<ChatResponse> chat(@CurrentUserId(required = false) UUID userId,
                                          @Valid @RequestPart("request") ChatPromptRequest request,
                                          @RequestPart(value = "images", required = false) List<MultipartFile> images,
                                          HttpServletRequest httpServletRequest) {
        return chatService.chat(userId, request, images, httpServletRequest);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    // API đổi tên hội thoại.
    @PatchMapping("/conversations/{conversationId}")
    public APIResponse<ConversationSummaryResponse> update(@CurrentUserId UUID userId,
                                                           @PathVariable UUID conversationId,
                                                           @Valid @RequestBody ConversationUpdateRequest request) {
        return chatService.update(userId, conversationId, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    // API xóa hội thoại.
    @DeleteMapping("/conversations/{conversationId}")
    public APIResponse<Void> delete(@CurrentUserId UUID userId,
                                    @PathVariable UUID conversationId) {
        return chatService.delete(userId, conversationId);
    }
}
