package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

// Kết quả tính giá nội bộ (BookingPriceCalculator): dòng dịch vụ + từng buổi đã có giá + tổng. Tiền cọc KHÔNG tính ở
// Booking — PaymentService quyết định theo chính sách hiệu lực (PAY-03 / PAY-08).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingQuote {
    List<ServiceQuote> items;

    List<OccurrenceSlot> occurrences;

    BigDecimal subtotal;

    BigDecimal totalAmount;
}
