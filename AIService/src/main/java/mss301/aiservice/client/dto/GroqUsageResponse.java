package mss301.aiservice.client.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroqUsageResponse {
    @JsonProperty("prompt_tokens")
    Integer promptTokens;

    @JsonProperty("completion_tokens")
    Integer completionTokens;

    @JsonProperty("total_tokens")
    Integer totalTokens;
}
