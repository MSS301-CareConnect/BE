package mss301.providerservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.Gender;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverUpdateRequest {

    @NotBlank
    @Size(max = 255)
    String fullName;

    Gender gender;

    @Past
    LocalDate dateOfBirth;

    @NotNull
    @Min(0)
    @Max(80)
    Integer experienceYears;

    @Size(max = 5000)
    String bio;

    @NotNull
    CaregiverStatus caregiverStatus;

    @NotNull
    Long version;
}
