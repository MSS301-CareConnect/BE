package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

// Chủ ví lấy từ token; không nhận walletId / ownerType / trạng thái từ request (chống mass assignment).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateWalletWithdrawalRequest {
    @NotNull(message = "WITHDRAWAL_AMOUNT_REQUIRED")
    @Positive(message = "WITHDRAWAL_AMOUNT_INVALID")
    @Digits(integer = 12, fraction = 0, message = "WITHDRAWAL_AMOUNT_INVALID")
    BigDecimal amount;

    @NotBlank(message = "BANK_NAME_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String bankName;

    @NotBlank(message = "BANK_ACCOUNT_NUMBER_REQUIRED")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "BANK_ACCOUNT_NUMBER_INVALID")
    String bankAccountNumber;

    @NotBlank(message = "ACCOUNT_HOLDER_NAME_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String accountHolderName;
}
