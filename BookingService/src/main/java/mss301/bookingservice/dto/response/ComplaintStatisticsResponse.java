package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintStatisticsResponse {
    long open;
    long inProgress;
    long resolved;
    long escalated;
}
