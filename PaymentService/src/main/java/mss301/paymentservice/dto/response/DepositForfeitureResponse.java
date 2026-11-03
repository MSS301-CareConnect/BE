package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.RefundReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// CR-4: một khoản cọc bị giữ của một buổi và bên được hưởng.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DepositForfeitureResponse {
    UUID id;

    UUID bookingId;

    UUID centerId;

    UUID occurrenceId;

    Instant occurrenceStartAt;

    BigDecimal amount;

    ForfeitureBeneficiary beneficiary;

    UUID caregiverId;

    RefundReason reason;

    String rule;

    Instant createdAt;
}
