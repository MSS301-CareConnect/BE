package mss301.identityservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStatisticsResponse {
    Map<String, Long> usersByRole;
    Map<String, Long> usersByStatus;
    long newCustomers;
}
