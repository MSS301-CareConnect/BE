package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Báo giá tạm tính trước khi đặt (BK-01). depositPercent = tỷ lệ khoản thanh toán đầu theo center policy (SHIFT 100,
// RECURRING / CUSTOM = cọc %); estimatedDepositAmount = khoản thanh toán đầu dự kiến; policyId null khi trung tâm dùng
// policy mặc định.
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
