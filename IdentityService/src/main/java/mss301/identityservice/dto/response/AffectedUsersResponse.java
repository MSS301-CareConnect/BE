package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AffectedUsersResponse {
    long affectedUsers;
}
