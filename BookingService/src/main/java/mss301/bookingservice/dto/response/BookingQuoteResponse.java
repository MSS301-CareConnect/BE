package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Báo giá tạm tính trước khi đặt (BK-01). Phần cọc chỉ để hiển thị; null khi PaymentService chưa trả chính sách.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookingQuoteResponse {
    int occurrenceCount;

    List<QuoteLineResponse> lines;

    List<QuoteOccurrenceResponse> occurrences;

    BigDecimal totalAmount;

    BigDecimal depositPercent;

    BigDecimal estimatedDepositAmount;

    UUID policyId;
}
