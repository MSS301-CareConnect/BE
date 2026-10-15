package mss301.bookingservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Body của POST /internal/users/batch (IdentityService giới hạn 100 id / lần).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityUserBatchRequest {
    public static final int MAX_SIZE = 100;

    List<UUID> userIds;
}
