package mss301.aiservice.client;

import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResult;

import java.util.List;

public interface GroqClient {
    // Chat: mô hình và temperature theo cấu hình ai.groq.*.
    GroqResult generateAnswer(List<GroqMessageRequest> messages);

    // Đọc ảnh chứng chỉ: mô hình đọc ảnh (ai.groq.vision-model) và temperature riêng.
    GroqResult generateAnswer(List<GroqMessageRequest> messages, String model, double temperature);
}
