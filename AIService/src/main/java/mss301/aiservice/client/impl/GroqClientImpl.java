package mss301.aiservice.client.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.client.GroqClient;
import mss301.aiservice.client.dto.GroqChatRequest;
import mss301.aiservice.client.dto.GroqChatResponse;
import mss301.aiservice.client.dto.GroqChoiceResponse;
import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResponseMessage;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.client.dto.GroqUsageResponse;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GroqClientImpl implements GroqClient {
    RestClient groqRestClient;
    AIProperties aiProperties;

    @Override
    public GroqResult generateAnswer(List<GroqMessageRequest> messages) {
        return generateAnswer(messages, aiProperties.getGroq().getModel(), aiProperties.getGroq().getTemperature());
    }

    @Override
    public GroqResult generateAnswer(List<GroqMessageRequest> messages, String model, double temperature) {
        if (messages == null || messages.isEmpty() || model == null || model.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        GroqChatRequest request = GroqChatRequest.builder()
                        .model(model)
                        .messages(messages)
                        .temperature(temperature)
                        .maxCompletionTokens(aiProperties.getGroq().getMaxCompletionTokens())
                        .stream(false)
                        .build();

        try {
            GroqChatResponse response = groqRestClient
                            .post()
                            .uri("/chat/completions")
                            .body(request)
                            .retrieve()
                            .body(GroqChatResponse.class);

            return extractResult(response);
        } catch (ResourceAccessException exception) {
            log.warn("Groq connection unavailable ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.GROQ_UNAVAILABLE);

        } catch (RestClientResponseException exception) {
            handleGroqHttpError(exception);

            throw new AppException(ErrorCode.GROQ_UNAVAILABLE);

        } catch (RestClientException exception) {
            log.warn("Groq request failed ({})", exception.getClass().getSimpleName());
            throw new AppException(ErrorCode.GROQ_UNAVAILABLE);
        }
    }

    private GroqResult extractResult(GroqChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AppException(ErrorCode.GROQ_INVALID_RESPONSE);
        }

        GroqChoiceResponse firstChoice = response.getChoices().getFirst();

        if (firstChoice == null) {
            throw new AppException(ErrorCode.GROQ_INVALID_RESPONSE);
        }

        GroqResponseMessage message = firstChoice.getMessage();

        if (message == null || message.getContent() == null || message.getContent().isBlank()) {
            throw new AppException(ErrorCode.GROQ_INVALID_RESPONSE);
        }

        GroqUsageResponse usage = response.getUsage();

        return GroqResult.builder()
                .answer(message.getContent().trim())
                .model(response.getModel() == null ? aiProperties.getGroq()
                                .getModel() : response.getModel())
                .promptTokens(usage == null ? null : usage.getPromptTokens())
                .completionTokens(usage == null ? null : usage.getCompletionTokens())
                .build();
    }

    private void handleGroqHttpError(RestClientResponseException exception) {
        HttpStatusCode statusCode = exception.getStatusCode();
        log.warn("Groq returned HTTP status {}", statusCode.value());

        if (statusCode.value() == 429 || statusCode.is5xxServerError()) {
            throw new AppException(ErrorCode.GROQ_UNAVAILABLE);
        }

        throw new AppException(ErrorCode.GROQ_REQUEST_REJECTED);
    }
}
