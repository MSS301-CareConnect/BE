package mss301.aiservice.service;

public interface ConversationTitleService {
    // Sinh tiêu đề hội thoại từ câu hỏi đầu tiên.
    String generateTitle(String firstPrompt);
}
