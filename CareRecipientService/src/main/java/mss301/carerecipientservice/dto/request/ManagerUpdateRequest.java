package mss301.carerecipientservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;

// Partial update: field null = giữ nguyên; phải có ít nhất một field (MANAGER_UPDATE_EMPTY).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManagerUpdateRequest {
    RecipientAccessLevel accessLevel;

    RelationshipType relationshipType;
}
