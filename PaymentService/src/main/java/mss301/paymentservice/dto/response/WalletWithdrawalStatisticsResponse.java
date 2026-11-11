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

/**
 * Dashboard yêu cầu rút tiền (2026-11-10) theo phạm vi người gọi. Đang chờ tính mọi thời điểm; còn lại tính trong kỳ
 * (gửi theo lúc tạo, đã chuyển theo lúc hoàn tất, từ chối theo lúc xử lý). Phần "giữ hộ" chỉ có ở hàng đợi trung tâm:
 * tiền của khách / caregiver gắn trung tâm còn trong ví (khả dụng + đang chờ rút) — trung tâm đang giữ thực tế.
 */
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
