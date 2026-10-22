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

    // caregiver_profiles.caregiver_id bên ProviderService; không bắt buộc (trung tâm phân công khi chấp nhận)
    UUID caregiverId;

    @NotNull(message = "SCHEDULE_TYPE_REQUIRED")
    ScheduleType scheduleType;

    @NotNull(message = "START_DATE_REQUIRED")
    LocalDate startDate;

    // Bắt buộc với DAILY / WEEKLY; ONE_TIME bỏ trống hoặc bằng startDate
    LocalDate endDate;

    @NotNull(message = "START_TIME_REQUIRED")
    LocalTime startTime;

    @NotNull(message = "END_TIME_REQUIRED")
    LocalTime endTime;

    // ISO 1 = Thứ Hai … 7 = Chủ Nhật; chỉ cho WEEKLY
    List<Integer> daysOfWeek;

    @Size(max = 2000, message = "TEXT_TOO_LONG")
    String customerNote;

    @NotEmpty(message = "SERVICES_REQUIRED")
    @Size(max = 10, message = "TOO_MANY_SERVICES")
    List<@Valid @NotNull(message = "SERVICES_REQUIRED") BookingServiceRequest> services;
}
