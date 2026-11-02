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

// Không có customerId / status / giá / caregiverUserId: lấy từ token, state machine, Catalog, Provider (chống mass assignment).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingCreateRequest implements BookingScheduleRequest {
    @NotNull(message = "RECIPIENT_ID_REQUIRED")
    UUID recipientId;

    @NotNull(message = "CENTER_ID_REQUIRED")
    UUID centerId;

    @NotNull(message = "BOOKING_TYPE_REQUIRED")
    BookingType bookingType;

    // caregiver_profiles.caregiver_id bên ProviderService; bắt buộc với SHIFT, tùy chọn với RECURRING / CUSTOM
    // (trung tâm phân công sau khi xác nhận).
    UUID caregiverId;

    // SHIFT: caregiver_schedules.schedule_id bên ProviderService.
    UUID caregiverScheduleId;

    // RECURRING: DAILY / WEEKLY.
    RecurrenceFrequency frequency;

    LocalDate startDate;

    LocalDate endDate;

    LocalTime startTime;

    LocalTime endTime;

    // ISO 1 = Thứ Hai … 7 = Chủ Nhật; chỉ cho RECURRING WEEKLY.
    List<Integer> daysOfWeek;

    // CUSTOM: từng buổi một khung giờ riêng.
    @Size(max = 366, message = "TOO_MANY_OCCURRENCES")
    List<@Valid @NotNull(message = "INVALID_TIME_SLOT") TimeSlotRequest> slots;

    @Size(max = 2000, message = "TEXT_TOO_LONG")
    String customerNote;

    @NotEmpty(message = "SERVICES_REQUIRED")
    @Size(max = 10, message = "TOO_MANY_SERVICES")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") BookingServiceRequest> services;
}
