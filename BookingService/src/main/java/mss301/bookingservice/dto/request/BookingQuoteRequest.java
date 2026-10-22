package mss301.bookingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// POST /api/v1/bookings/quotes — cùng hình dạng với BookingCreateRequest để FE gửi lại đúng body sẽ đặt; người bệnh /
// caregiver / ghi chú không ảnh hưởng giá nên không bắt buộc. Không ghi DB.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingQuoteRequest implements BookingScheduleRequest {
    UUID recipientId;

    @NotNull(message = "CENTER_ID_REQUIRED")
    UUID centerId;

    UUID caregiverId;

    @NotNull(message = "SCHEDULE_TYPE_REQUIRED")
    ScheduleType scheduleType;

    @NotNull(message = "START_DATE_REQUIRED")
    LocalDate startDate;

    LocalDate endDate;

    @NotNull(message = "START_TIME_REQUIRED")
    LocalTime startTime;

    @NotNull(message = "END_TIME_REQUIRED")
    LocalTime endTime;

    List<Integer> daysOfWeek;

    @Size(max = 2000, message = "TEXT_TOO_LONG")
    String customerNote;

    @NotEmpty(message = "SERVICES_REQUIRED")
    @Size(max = 10, message = "TOO_MANY_SERVICES")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") BookingServiceRequest> services;
}
