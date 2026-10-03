package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientManagerResponse {
    UUID id;

    UUID userId;

    RelationshipType relationshipType;

    RecipientAccessLevel accessLevel;

    boolean primary;

    UUID addedBy;

    Instant createdAt;

    // false khi IdentityService không phản hồi (fallback) hoặc tài khoản không còn — các field profile bên dưới null
    boolean profileAvailable;

    String fullName;

    String email;

    String phoneNumber;

    String userStatus;
}
