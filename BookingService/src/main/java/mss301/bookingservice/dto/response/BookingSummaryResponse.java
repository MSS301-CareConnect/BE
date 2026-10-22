package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.ScheduleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// Dòng danh sách booking. customer chỉ có ở màn hình trung tâm / admin; tên người bệnh lấy từ snapshot lúc đặt.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingSummaryResponse {
    UUID id;

    String bookingCode;

    BookingStatus status;

    UUID customerId;

    CustomerProfileResponse customer;

    UUID recipientId;

    String recipientName;

    UUID centerId;

    UUID caregiverId;

    ScheduleType scheduleType;

    LocalDate startDate;

    LocalDate endDate;

    LocalTime startTime;

    LocalTime endTime;

    List<Integer> daysOfWeek;

    BigDecimal totalAmount;

    Instant createdAt;

    // BK-06: buổi kế tiếp chưa kết thúc (null khi không còn), số buổi đã hoàn thành / tổng số buổi.
    Instant nextOccurrenceAt;

    int completedCount;

    int totalCount;
}
