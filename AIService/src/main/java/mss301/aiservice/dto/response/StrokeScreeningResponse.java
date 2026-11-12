package mss301.aiservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrokeScreeningResponse {
    Integer predictedClass;
    Double modelScore;
    String screeningResult;
    String recommendation;
    String disclaimer;
}
