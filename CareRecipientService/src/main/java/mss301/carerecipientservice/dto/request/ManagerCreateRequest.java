package mss301.carerecipientservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import mss301.carerecipientservice.enums.RelationshipType;

// Thêm người thân bằng email tài khoản CUSTOMER (tra cứu qua IdentityService). Không nhận userId từ client.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ManagerCreateRequest {
    @NotBlank(message = "EMAIL_REQUIRED")
    @Email(message = "INVALID_EMAIL_FORMAT")
    @Size(max = 255, message = "INVALID_EMAIL_FORMAT")
    String email;

    @NotNull(message = "RELATIONSHIP_TYPE_REQUIRED")
    RelationshipType relationshipType;

    @NotNull(message = "ACCESS_LEVEL_REQUIRED")
    RecipientAccessLevel accessLevel;
}
