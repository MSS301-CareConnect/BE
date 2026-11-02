package mss301.bookingservice.service;

import mss301.bookingservice.client.dto.ProviderScheduleResponse;
import mss301.bookingservice.dto.request.BookingScheduleRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;

/**
 * Sinh các buổi chăm sóc theo loại booking (spec §4) theo múi giờ booking.schedule.timezone, trả về thời điểm UTC đã
 * sắp theo giờ bắt đầu. Thuần tính toán (không DB, không mạng) — vi phạm quy tắc lịch → AppException với ErrorCode 52xx.
 * Thời gian đặt trước tối thiểu phụ thuộc center policy nên service kiểm sau khi sinh.
 */
public interface OccurrenceGenerator {
    // Field của request phải khớp loại booking (SHIFT cần caregiverId + caregiverScheduleId, …) — gọi trước khi tra Provider.
    void checkShape(BookingScheduleRequest request);

    // RECURRING / CUSTOM.
    List<OccurrenceSlot> generate(BookingScheduleRequest request);

    // SHIFT: đúng một buổi theo lịch caregiver đã xác minh với ProviderService.
    List<OccurrenceSlot> generateShift(BookingScheduleRequest request, ProviderScheduleResponse schedule);
}
