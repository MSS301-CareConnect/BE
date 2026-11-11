package mss301.aiservice.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatPromptRequest {
    @NotBlank(message = "PROMPT_REQUIRED")
    String prompt;

    UUID conversationId;
}
