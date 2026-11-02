package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// Bản sao ScheduleResponse của ProviderService (GET /api/v1/caregivers/schedules/{id}, token SERVICE). Ngày / giờ ca là
// giờ địa phương Việt Nam như work_shifts.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderScheduleResponse {
    public static final String STATUS_SCHEDULED = "SCHEDULED";

    UUID id;

    UUID caregiverId;

    UUID centerId;

    LocalDate workDate;

    LocalTime startTime;

    LocalTime endTime;

    String scheduleStatus;
}
