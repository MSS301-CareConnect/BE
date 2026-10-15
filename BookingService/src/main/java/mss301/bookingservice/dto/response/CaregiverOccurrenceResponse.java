package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.Instant;
import java.util.UUID;

// Buổi chăm sóc kèm thông tin booking cho caregiver / service nội bộ. recipientSnapshot chỉ có ở chi tiết.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaregiverOccurrenceResponse {
    UUID id;

    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    Integer durationMinutes;

    OccurrenceStatus status;

    UUID caregiverId;

    UUID caregiverUserId;

    UUID bookingId;

    String bookingCode;

    BookingStatus bookingStatus;

    UUID centerId;

    UUID recipientId;

    String recipientName;

    String serviceAddress;

    String customerNote;

    RecipientSnapshot recipientSnapshot;
}
