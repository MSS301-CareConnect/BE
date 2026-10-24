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

    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 255, message = "INVALID_REQUEST")
    String fullName;

    Gender gender;

    @Past(message = "INVALID_REQUEST")
    LocalDate dateOfBirth;

    @NotNull(message = "INVALID_REQUEST")
    @Min(value = 0, message = "INVALID_REQUEST")
    @Max(value = 80, message = "INVALID_REQUEST")
    Integer experienceYears;

    @Size(max = 5000, message = "INVALID_REQUEST")
    String bio;

    @NotNull(message = "INVALID_REQUEST")
    Long version;
}
