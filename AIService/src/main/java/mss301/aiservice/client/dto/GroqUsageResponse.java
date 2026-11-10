package mss301.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroqUsageResponse {
    @JsonProperty("prompt_tokens")
    Integer promptTokens;

    @JsonProperty("completion_tokens")
    Integer completionTokens;

    @JsonProperty("total_tokens")
    Integer totalTokens;
}
