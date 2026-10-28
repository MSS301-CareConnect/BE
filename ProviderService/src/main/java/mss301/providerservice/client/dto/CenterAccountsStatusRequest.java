package mss301.providerservice.client.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterAccountsStatusRequest {
    String status;
    String reason;
}
