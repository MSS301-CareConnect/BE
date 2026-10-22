package mss301.carerecipientservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Body của POST /api/v1/users/batch (IdentityService giới hạn 100 id / lần).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityUserBatchRequest {
    public static final int MAX_SIZE = 100;

    List<UUID> userIds;
}
