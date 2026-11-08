package mss301.aiservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
