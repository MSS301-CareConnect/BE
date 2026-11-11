package mss301.aiservice.dto.response;

import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.enums.MessageRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageResponse {
    UUID chatMessageId;

    MessageRole messageRole;

    String content;

    List<ChatImageResponse> images;

    Instant createdAt;
}
