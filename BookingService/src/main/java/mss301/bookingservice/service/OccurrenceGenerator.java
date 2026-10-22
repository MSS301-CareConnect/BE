package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BookingScheduleRequest;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;

/**
 * Sinh các buổi chăm sóc từ quy tắc lịch (ONE_TIME / DAILY / WEEKLY) theo múi giờ booking.schedule.timezone, trả về
 * thời điểm UTC. Thuần tính toán (không DB, không mạng) — vi phạm quy tắc lịch → AppException với ErrorCode 52xx.
 */
public interface OccurrenceGenerator {
    List<OccurrenceSlot> generate(BookingScheduleRequest request);
}
