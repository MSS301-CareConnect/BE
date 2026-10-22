package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;

import java.util.List;

/**
 * Tính giá từng buổi (BK-01, D12) — thuần tính toán, không DB / mạng. Số đơn vị mỗi buổi: HOUR = số giờ làm tròn lên,
 * DAY = 1, SHIFT = 1 (buổi không được dài hơn thời lượng gói). Giá buổi = Σ dịch vụ (đơn giá × đơn vị), làm tròn đồng;
 * tổng = Σ giá buổi.
 */
public interface BookingPriceCalculator {
    BookingQuote calculate(List<ServiceQuote> services, List<OccurrenceSlot> slots);
}
