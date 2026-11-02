package mss301.bookingservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// POST /api/v1/bookings/quotes — cùng hình dạng với BookingCreateRequest để FE gửi lại đúng body sẽ đặt; người bệnh /
// ghi chú không ảnh hưởng giá nên không bắt buộc. Không ghi DB.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingQuoteRequest implements BookingScheduleRequest {
    UUID recipientId;

    @NotNull(message = "CENTER_ID_REQUIRED")
    UUID centerId;

    @NotNull(message = "BOOKING_TYPE_REQUIRED")
    BookingType bookingType;

    UUID caregiverId;

    UUID caregiverScheduleId;

    RecurrenceFrequency frequency;

    LocalDate startDate;

    LocalDate endDate;

    LocalTime startTime;

    LocalTime endTime;

    List<Integer> daysOfWeek;

    @Size(max = 366, message = "TOO_MANY_OCCURRENCES")
    List<@Valid @NotNull(message = "INVALID_TIME_SLOT") TimeSlotRequest> slots;

    @Size(max = 2000, message = "TEXT_TOO_LONG")
    String customerNote;

    @NotEmpty(message = "SERVICES_REQUIRED")
    @Size(max = 10, message = "TOO_MANY_SERVICES")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") BookingServiceRequest> services;
}
