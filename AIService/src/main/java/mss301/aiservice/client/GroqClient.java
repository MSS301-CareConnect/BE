package mss301.aiservice.client;

import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResult;

import java.util.List;

public interface GroqClient {
    GroqResult generateAnswer(List<GroqMessageRequest> messages);
}
