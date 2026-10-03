package mss301.identityservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserBatchRequest {
    @NotEmpty(message = "USER_IDS_REQUIRED")
    @Size(max = 100, message = "USER_BATCH_TOO_LARGE")
    List<@NotNull(message = "USER_IDS_REQUIRED") UUID> userIds;
}
