package mss301.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Đổi tên cuộc trò chuyện. version = version client đọc được; lệch → 409 INVALID_CONVERSATION_VERSION.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationUpdateRequest {
    @NotBlank(message = "CONVERSATION_TITLE_REQUIRED")
    @Size(max = 120, message = "CONVERSATION_TITLE_TOO_LONG")
    String title;

    @NotNull(message = "INVALID_CONVERSATION_VERSION")
    Long version;
}
