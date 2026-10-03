package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccessCheckResponse {
    UUID recipientId;

    UUID userId;

    boolean allowed;

    // Quyền thực tế của user (null nếu không quản lý hồ sơ)
    RecipientAccessLevel accessLevel;
}
