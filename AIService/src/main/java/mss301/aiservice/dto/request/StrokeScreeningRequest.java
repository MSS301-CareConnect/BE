package mss301.aiservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.enums.Sex;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StrokeScreeningRequest {
    @NotNull(message = "Hypertension information is required")
    Boolean hypertension;

    @NotNull(message = "High cholesterol information is required")
    Boolean highCholesterol;

    @NotNull(message = "Myocardial infarction history is required")
    Boolean myocardialInfarctionHistory;

    @NotNull(message = "Angina or CHD history is required")
    Boolean anginaOrChdHistory;

    @NotNull(message = "Diabetes information is required")
    Boolean diabetes;

    @NotNull(message = "Sex is required")
    Sex sex;

    @NotNull(message = "Age is required")
    @Min(value = 18, message = "Age must be at least 18")
    @Max(value = 120, message = "Age must not exceed 120")
    Integer age;

    @NotNull(message = "BMI is required")
    @DecimalMin(value = "10.0", message = "BMI must be at least 10")
    @DecimalMax(value = "80.0", message = "BMI must not exceed 80")
    Double bmi;

    @NotNull(message = "Current smoker information is required")
    Boolean currentSmoker;
}
