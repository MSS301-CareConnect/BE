package mss301.bookingservice.dto.request;

import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// Phần lịch dùng chung cho tạo booking và báo giá (spec §4) — OccurrenceGenerator chỉ cần chừng này.
// SHIFT: caregiverId + caregiverScheduleId; RECURRING: frequency + khoảng ngày + giờ (+ daysOfWeek nếu WEEKLY);
// CUSTOM: danh sách slots. Field không thuộc loại đã chọn phải để trống.
public interface BookingScheduleRequest {
    BookingType getBookingType();

    UUID getCaregiverId();

    UUID getCaregiverScheduleId();

    RecurrenceFrequency getFrequency();

    LocalDate getStartDate();

    LocalDate getEndDate();

    LocalTime getStartTime();

    LocalTime getEndTime();

    List<Integer> getDaysOfWeek();

    List<TimeSlotRequest> getSlots();
}
