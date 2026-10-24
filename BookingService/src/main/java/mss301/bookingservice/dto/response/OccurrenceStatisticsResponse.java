package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OccurrenceStatisticsResponse {
    long completed;
    long cancelled;
    long missed;
    long customerNoShow;
}
