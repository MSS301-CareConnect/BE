package mss301.aiservice.client.dto;

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
public class StrokeModelClientRequest {
    Boolean hypertension;
    Boolean highCholesterol;
    Boolean myocardialInfarctionHistory;
    Boolean anginaOrChdHistory;
    Boolean diabetes;
    String sex;
    Integer age;
    Double bmi;
    Boolean currentSmoker;
}
