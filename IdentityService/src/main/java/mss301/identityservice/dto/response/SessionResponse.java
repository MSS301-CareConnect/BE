package mss301.identityservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionResponse {
    String sessionId;
    String deviceId;
    String userAgent;
    String ip;
    Instant createdAt;
    Instant lastActiveAt;

    @JsonProperty("isCurrent")
    boolean current;
}
