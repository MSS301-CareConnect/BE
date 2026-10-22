package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Dữ liệu PaymentService cần để tạo phiên cọc / tính hoàn tiền (C3.2, token SERVICE).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentContextResponse {
    UUID bookingId;

    String bookingCode;

    UUID customerId;

    UUID centerId;

    BookingStatus status;

    BigDecimal totalAmount;

    Instant depositDueAt;

    Instant firstStartAt;

    Instant depositPaidAt;

    List<PaymentContextOccurrenceResponse> occurrences;
}
