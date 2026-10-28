package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderStatisticsResponse {
    Map<String, Long> centersByStatus;
    Map<String, Long> caregiversByStatus;
    Map<String, Long> caregiversByVerification;
}
