package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;

import java.util.List;

public interface BookingPriceCalculator {
    // Tính giá từng buổi và tổng tiền từ báo giá dịch vụ.
    BookingQuote calculate(List<ServiceQuote> services, List<OccurrenceSlot> slots);
}
