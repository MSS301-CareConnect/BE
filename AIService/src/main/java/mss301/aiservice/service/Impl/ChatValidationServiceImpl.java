package mss301.aiservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.dto.request.ChatPromptRequest;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.service.ChatValidationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatValidationServiceImpl implements ChatValidationService {
    AIProperties aiProperties;

    // Kiểm tra câu hỏi, chặn khách dùng hội thoại cũ, kiểm tra ảnh gửi kèm.
    @Override
    public String validateAndNormalize(UUID userId, ChatPromptRequest request, List<MultipartFile> images) {
        String normalizedPrompt =validatePrompt(userId, request);

        if (userId == null && request.getConversationId() != null) {
            throw new AppException(ErrorCode.GUEST_CONVERSATION_NOT_ALLOWED);
        }

        validateImages(userId, images);
        return normalizedPrompt;
    }

    // Kiểm tra câu hỏi không rỗng và không vượt độ dài cho phép.
    private String validatePrompt(UUID userId, ChatPromptRequest request) {
        if (request == null || request.getPrompt() == null) {
            throw new AppException(ErrorCode.PROMPT_REQUIRED);
        }

        String normalizedPrompt = request.getPrompt().trim();

        if (normalizedPrompt.isEmpty()) {
            throw new AppException(ErrorCode.PROMPT_REQUIRED);
        }

        int maxLength;

        if (userId == null) {
            maxLength = aiProperties.getChat().getGuestMaxPromptLength();

            if (normalizedPrompt.length() > maxLength) {
                throw new AppException(ErrorCode.GUEST_PROMPT_TOO_LONG);
            }
        } else {
            maxLength = aiProperties.getChat().getCustomerMaxPromptLength();

            if (normalizedPrompt.length() > maxLength) {
                throw new AppException(ErrorCode.CUSTOMER_PROMPT_TOO_LONG);
            }
        }

        return normalizedPrompt;
    }

    // Kiểm tra số lượng, tổng dung lượng ảnh; khách không được gửi ảnh.
    private void validateImages(UUID userId, List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return;
        }

        if (userId == null) {
            throw new AppException(ErrorCode.GUEST_IMAGE_NOT_ALLOWED);
        }

        int maxImages = aiProperties.getChat().getMaxImages();

        if (images.size() > maxImages) {
            throw new AppException(ErrorCode.TOO_MANY_IMAGES);
        }

        long maxFileSize = megabytesToBytes(aiProperties.getChat().getMaxImageSizeMb());

        long maxTotalSize = megabytesToBytes(aiProperties.getChat().getMaxTotalImageSizeMb());

        long totalSize = 0;

        for (MultipartFile image : images) {
            validateSingleImage(image, maxFileSize);

            totalSize += image.getSize();

            if (totalSize > maxTotalSize) {
                throw new AppException(ErrorCode.TOTAL_IMAGE_SIZE_TOO_LARGE);
            }
        }
    }

    // Kiểm tra một ảnh: không rỗng, đúng dung lượng, đuôi và loại file.
    private void validateSingleImage(MultipartFile image, long maxFileSize) {
        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.EMPTY_IMAGE);
        }

        if (image.getSize() > maxFileSize) {
            throw new AppException(ErrorCode.IMAGE_TOO_LARGE);
        }

        String contentType = image.getContentType();

        if (contentType == null) {
            throw new AppException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT);
        String extension = extractExtension(image.getOriginalFilename());

        boolean valid;

        if (MediaType.IMAGE_JPEG_VALUE.equals(normalizedContentType)) {
            valid = "jpg".equals(extension) || "jpeg".equals(extension);

        } else if (MediaType.IMAGE_PNG_VALUE.equals(normalizedContentType)) {
            valid = "png".equals(extension);

        } else if ("image/webp".equals(normalizedContentType)) {
            valid = "webp".equals(extension);

        } else {
            valid = false;
        }

        if (!valid) {
            throw new AppException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    // Lấy đuôi file ảnh từ tên file gốc.
    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new AppException(ErrorCode.INVALID_IMAGE_NAME);
        }

        String normalizedFilename = originalFilename.trim().toLowerCase(Locale.ROOT);
        int lastDot = normalizedFilename.lastIndexOf('.');

        if (lastDot <= 0 || lastDot == normalizedFilename.length() - 1) {
            throw new AppException(ErrorCode.INVALID_IMAGE_NAME);
        }

        return normalizedFilename.substring(lastDot + 1);
    }

    // Đổi MB sang byte.
    private long megabytesToBytes(int megabytes) {
        return megabytes * 1024L * 1024L;
    }
}
