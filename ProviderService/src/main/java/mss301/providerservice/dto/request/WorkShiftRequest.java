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

    @NotNull
    LocalDate workDate;

    @NotNull
    LocalTime startTime;

    @NotNull
    LocalTime endTime;

    @NotBlank
    @Size(max = 50)
    String shiftType;

    @NotNull
    ShiftStatus shiftStatus;

    @NotNull
    @Min(1)
    @Max(1000)
    Integer capacity;

    Instant registrationDeadline;

    @Size(max = 5000)
    String note;

    Long version;
}
