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

// Chi tiết booking + dịch vụ + các buổi. customer chỉ có ở màn hình trung tâm / admin.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingDetailResponse {
    UUID id;

    String bookingCode;

    BookingStatus status;

    UUID customerId;

    CustomerProfileResponse customer;

    UUID recipientId;

    UUID centerId;

    UUID caregiverId;

    UUID caregiverUserId;

    ScheduleType scheduleType;

    LocalDate startDate;

    LocalDate endDate;

    LocalTime startTime;

    LocalTime endTime;

    List<Integer> daysOfWeek;

    String timezone;

    RecipientSnapshot recipientSnapshot;

    String serviceAddress;

    String customerNote;

    // NULL tới khi nối CareCatalogService
    BigDecimal subtotal;

    BigDecimal depositAmount;

    BigDecimal totalAmount;

    Instant acceptanceDueAt;

    Instant acceptedAt;

    Instant depositDueAt;

    Instant confirmedAt;

    Instant cancelledAt;

    UUID cancelledBy;

    String cancelReason;

    Instant rejectedAt;

    String rejectedReason;

    Instant createdAt;

    Instant updatedAt;

    Long version;

    List<BookingItemResponse> services;

    List<OccurrenceResponse> occurrences;
}
