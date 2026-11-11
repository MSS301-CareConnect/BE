package mss301.aiservice.service.Impl;

import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.service.ConversationTitleService;
import org.springframework.stereotype.Service;

@Service
public class ConversationTitleServiceImpl implements ConversationTitleService {
    static int MAX_TITLE_LENGTH = 60;

    static int CONTENT_LENGTH_WITH_ELLIPSIS = MAX_TITLE_LENGTH - 3;

    // Sinh tiêu đề từ câu hỏi đầu tiên, cắt ngắn nếu quá dài.
    @Override
    public String generateTitle(String firstPrompt) {
        if (firstPrompt == null || firstPrompt.isBlank()) {
            throw new AppException(ErrorCode.PROMPT_REQUIRED);
        }

        String normalizedPrompt = firstPrompt.trim().replaceAll("\\s+", " ");

        if (normalizedPrompt.length() <= MAX_TITLE_LENGTH) {
            return normalizedPrompt;
        }

        String shortenedTitle = normalizedPrompt.substring(0, CONTENT_LENGTH_WITH_ELLIPSIS).trim();
        return shortenedTitle + "...";
    }
}
