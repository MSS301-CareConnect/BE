package mss301.bookingservice.dto.request;

import mss301.bookingservice.enums.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Phần quy tắc lịch dùng chung cho tạo booking và báo giá — OccurrenceGenerator chỉ cần chừng này.
public interface BookingScheduleRequest {
    ScheduleType getScheduleType();

    LocalDate getStartDate();

    LocalDate getEndDate();

    LocalTime getStartTime();

    LocalTime getEndTime();

    List<Integer> getDaysOfWeek();
}
