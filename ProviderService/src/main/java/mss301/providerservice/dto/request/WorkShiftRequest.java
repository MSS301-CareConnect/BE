package mss301.providerservice.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.ShiftStatus;

import java.time.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftRequest {
    @NotNull LocalDate workDate;
    @NotNull LocalTime startTime;
    @NotNull LocalTime endTime;
    @NotBlank @Size(max = 50) String shiftType;
    @NotNull ShiftStatus shiftStatus;
    @NotNull @Min(1) @Max(1000) Integer capacity;
    Instant registrationDeadline;
    @Size(max = 5000) String note;
    Long version;
}
