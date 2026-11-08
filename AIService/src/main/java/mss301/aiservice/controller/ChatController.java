package mss301.aiservice.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.dto.response.APIResponse;
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

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirements({})
    public APIResponse<ChatResponse> chat(@CurrentUserId(required = false) UUID userId,
                                          @Valid @RequestPart("request") ChatPromptRequest request,
                                          @RequestPart(value = "images", required = false) List<MultipartFile> images,
                                          HttpServletRequest httpServletRequest) {
        return chatService.chat(userId, request, images, httpServletRequest);
    }

    @GetMapping("/conversations")
    public APIResponse<List<ConversationSummaryResponse>> getConversations(@CurrentUserId UUID userId) {
        return chatService.getConversations(userId);
    }

    @GetMapping("/conversations/{conversationId}")
    public APIResponse<ConversationDetailResponse> getConversation(@CurrentUserId UUID userId,
                                                                   @PathVariable UUID conversationId) {
        return chatService.getConversation(userId, conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public APIResponse<Void> deleteConversation(@CurrentUserId UUID userId,
                                                @PathVariable UUID conversationId) {
        return chatService.deleteConversation(userId, conversationId);
    }
}
