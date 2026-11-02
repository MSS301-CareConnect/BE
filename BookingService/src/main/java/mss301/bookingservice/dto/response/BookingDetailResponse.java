package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;

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

    BookingType bookingType;

    RecurrenceFrequency recurrenceFrequency;

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

    // Tỷ lệ khoản thanh toán đầu (SHIFT 100, RECURRING / CUSTOM = cọc %) chụp từ center policy lúc tạo.
    BigDecimal initialPaymentPercent;

    BigDecimal totalAmount;

    Instant acceptanceDueAt;

    Instant acceptedAt;

    // Hạn phân caregiver (PENDING_CAREGIVER_ASSIGNMENT).
    Instant assignmentDueAt;

    // Hạn thanh toán khoản đầu (AWAITING_INITIAL_PAYMENT).
    Instant depositDueAt;

    // RECURRING / CUSTOM: hạn trả xong phần còn lại trước buổi đầu.
    Instant remainingPaymentDueAt;

    // Đã trả đủ (spec §9.1); NULL khi còn phần chưa trả.
    Instant fullyPaidAt;

    Instant confirmedAt;

    Instant startedAt;

    Instant completedAt;

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

    // Chỉ có ở response hủy khi booking đã đặt cọc (BK-03 / BK-04).
    CancellationRefundResponse refund;
}
