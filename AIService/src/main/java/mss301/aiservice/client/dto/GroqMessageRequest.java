package mss301.aiservice.client.dto;

import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroqMessageRequest {
    String role;

    Object content;

    public static GroqMessageRequest text(String role, String content) {
        return GroqMessageRequest.builder()
                .role(role)
                .content(content)
                .build();
    }
}
