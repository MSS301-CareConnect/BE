package mss301.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletStatisticsResponse {
    WalletOwnerType ownerType;

    Instant from;

    Instant to;

    StatisticsGroupBy groupBy;

    BigDecimal availableBalance;

    BigDecimal heldBalance;

    // Thu nhập thuần trong kỳ (khách: tiền hoàn vào ví; trung tâm: tiền buổi + cọc giữ − trừ khiếu nại).
    BigDecimal totalIncome;

    BigDecimal totalWithdrawn;

    Long bookingCount;

    List<WalletTypeTotalResponse> byType;

    List<WalletSeriesPointResponse> series;

    // CUSTOMER
    BigDecimal totalPaid;

    BigDecimal refundedToWallet;

    BigDecimal refundedToGateway;

    BigDecimal netSpent;

    // CAREGIVER / CENTER
    BigDecimal forfeitIncome;

    // CENTER
    BigDecimal serviceIncome;

    BigDecimal commissionDeducted;

    BigDecimal complaintDebits;

    // PLATFORM
    BigDecimal commissionIncome;

    BigDecimal grossCollected;

    BigDecimal totalRefunded;

    List<WalletBalanceTotalResponse> liabilities;

    Long pendingWithdrawalCount;

    BigDecimal pendingWithdrawalAmount;
}
