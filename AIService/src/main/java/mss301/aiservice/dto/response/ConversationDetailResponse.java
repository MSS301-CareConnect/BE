package mss301.aiservice.dto.response;

import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationDetailResponse {
    UUID conversationId;

    String title;

    List<ChatMessageResponse> messages;

    Instant createdAt;

    Instant updatedAt;

    Long version;
}
