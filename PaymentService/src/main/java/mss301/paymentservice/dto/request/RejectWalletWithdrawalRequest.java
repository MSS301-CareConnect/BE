package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RejectWalletWithdrawalRequest {
    @NotBlank(message = "REJECT_REASON_REQUIRED")
    @Size(max = 500, message = "TEXT_TOO_LONG")
    String reason;
}
