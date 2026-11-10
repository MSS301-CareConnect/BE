package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.InvitationStatus;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;

import java.time.Instant;
import java.util.UUID;

// Góc nhìn của người mời: chỉ email chính họ đã nhập — không có tên, SĐT hay việc email có tài khoản hay không (C-04).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManagerInvitationResponse {
    UUID id;

    UUID recipientId;

    String email;

    RelationshipType relationshipType;

    RecipientAccessLevel accessLevel;

    InvitationStatus status;

    Instant expiresAt;

    Instant respondedAt;

    Instant createdAt;
}
