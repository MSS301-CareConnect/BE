package mss301.providerservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftRequest {

    @NotNull(message = "INVALID_REQUEST")
    LocalDate workDate;

    @NotNull(message = "INVALID_REQUEST")
    LocalTime startTime;

    @NotNull(message = "INVALID_REQUEST")
    LocalTime endTime;

    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 50, message = "INVALID_REQUEST")
    String shiftType;

    @NotNull(message = "INVALID_REQUEST")
    ShiftStatus shiftStatus;

    @NotNull(message = "INVALID_REQUEST")
    @Min(value = 1, message = "INVALID_REQUEST")
    @Max(value = 1000, message = "INVALID_REQUEST")
    Integer capacity;

    Instant registrationDeadline;

    @Size(max = 5000, message = "INVALID_REQUEST")
    String note;

    Long version;
}
