package mss301.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletWithdrawalScope;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletWithdrawalStatisticsResponse {
    WalletWithdrawalScope scope;

    Instant from;

    Instant to;

    StatisticsGroupBy groupBy;

    long pendingCount;

    BigDecimal pendingAmount;

    long requestedCount;

    BigDecimal requestedAmount;

    long completedCount;

    BigDecimal completedAmount;

    long rejectedCount;

    BigDecimal rejectedAmount;

    // Thời gian xử lý trung bình (phút, từ lúc gửi tới lúc chuyển xong) của yêu cầu hoàn tất trong kỳ; null khi chưa có.
    Long avgProcessingMinutes;

    List<WithdrawalOwnerTotalResponse> byOwnerType;

    List<WithdrawalSeriesPointResponse> series;

    // CENTER_MANAGER — REVIEW
    BigDecimal custodyCustomerBalance;

    BigDecimal custodyCaregiverBalance;

    BigDecimal custodyHeldBalance;

    BigDecimal custodyTotal;
}
