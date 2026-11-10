package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Admin đã chuyển khoản thủ công — mã giao dịch ngân hàng để đối soát.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CompleteWalletWithdrawalRequest {
    @NotBlank(message = "TRANSFER_REFERENCE_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String transferReference;
}
