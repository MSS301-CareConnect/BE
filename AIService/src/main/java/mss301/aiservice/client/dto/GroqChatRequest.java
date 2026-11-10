package mss301.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
