package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

// Báo giá cả booking. Tổng tiền NULL khi chưa nối Catalog / chưa chốt % đặt cọc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingQuote {
    List<ServiceQuote> items;

    BigDecimal subtotal;

    BigDecimal depositAmount;

    BigDecimal totalAmount;
}
