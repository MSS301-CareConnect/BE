package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Một buổi trong payment-context. depositAmount do PaymentService ghi (PAY-08) — tới lúc đó luôn null.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentContextOccurrenceResponse {
    UUID occurrenceId;

    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    OccurrenceStatus status;

    BigDecimal priceAmount;

    BigDecimal depositAmount;

    UUID caregiverId;

    // CR-4: PaymentService chụp caregiver từng buổi để ghi ai hưởng phần cọc bị giữ.
    UUID caregiverUserId;

    boolean hasSession;

    // 2026-11-11: ca tự đóng đang chờ trung tâm xác nhận — PaymentService chưa quyết toán buổi này.
    boolean settlementHeld;
}
