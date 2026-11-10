package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;

import java.time.Instant;
import java.util.UUID;

// Góc nhìn của người được mời: tên hồ sơ để quyết định chấp nhận; chi tiết y tế chỉ thấy sau khi chấp nhận.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MyInvitationResponse {
    UUID id;

    UUID recipientId;

    String recipientFullName;

    RelationshipType relationshipType;

    RecipientAccessLevel accessLevel;

    Instant expiresAt;

    Instant createdAt;
}
