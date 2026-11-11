package mss301.aiservice.client.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroqChatRequest {
    String model;

    List<GroqMessageRequest> messages;

    Double temperature;

    @JsonProperty("max_completion_tokens")
    Integer maxCompletionTokens;

    @Builder.Default
    Boolean stream = false;
}
