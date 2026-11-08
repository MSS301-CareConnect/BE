package mss301.aiservice.service;

import mss301.aiservice.dto.request.ChatPromptRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ChatValidationService {
    String validateAndNormalize(UUID userId, ChatPromptRequest request, List<MultipartFile> images);
}
