package mss301.paymentservice.mapper;

import mss301.paymentservice.client.dto.IdentityUserResponse;
import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalStatisticsResponse;
import mss301.paymentservice.dto.response.WithdrawalOwnerTotalResponse;
import mss301.paymentservice.dto.response.WithdrawalSeriesPointResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalScope;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface WalletWithdrawalMapper {

    // Ví, người yêu cầu, trung tâm xử lý (routedCenterId — service chọn, không lấy thẳng request.centerId), trạng thái do
    // service quyết định; phần duyệt / chuyển khoản trống tới khi trung tâm / admin xử lý.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "centerId", source = "routedCenterId")
    @Mapping(target = "rejectReason", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "transferReference", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    WalletWithdrawal toWithdrawal(CreateWalletWithdrawalRequest request, Wallet wallet, UUID requesterUserId,
                                  UUID routedCenterId, WalletWithdrawalStatus status);

    // Lệnh ghi (tạo / chuyển / từ chối) truyền Map.of(): không gọi Identity trong transaction ghi — FE đọc lại danh sách.
    @Mapping(target = "requesterName",
            expression = "java(requesters.containsKey(withdrawal.getRequesterUserId()) ? requesters.get(withdrawal.getRequesterUserId()).getFullName() : null)")
    @Mapping(target = "requesterPhoneNumber",
            expression = "java(requesters.containsKey(withdrawal.getRequesterUserId()) ? requesters.get(withdrawal.getRequesterUserId()).getPhoneNumber() : null)")
    WalletWithdrawalResponse toResponse(WalletWithdrawal withdrawal,
                                        @Context Map<UUID, IdentityUserResponse> requesters);

    List<WalletWithdrawalResponse> toResponseList(List<WalletWithdrawal> withdrawals,
                                                  @Context Map<UUID, IdentityUserResponse> requesters);

    WithdrawalSeriesPointResponse toSeriesPoint(String period, BigDecimal requested, BigDecimal completed);

    WithdrawalOwnerTotalResponse toOwnerTotal(WalletOwnerType ownerType, long pendingCount, BigDecimal pendingAmount,
                                              long completedCount, BigDecimal completedAmount);

    // Phần giữ hộ chỉ có ở hàng đợi trung tâm — service gán sau (bỏ khỏi JSON khi null).
    @Mapping(target = "custodyCustomerBalance", ignore = true)
    @Mapping(target = "custodyCaregiverBalance", ignore = true)
    @Mapping(target = "custodyHeldBalance", ignore = true)
    @Mapping(target = "custodyTotal", ignore = true)
    WalletWithdrawalStatisticsResponse toStatisticsResponse(WalletWithdrawalScope scope, Instant from, Instant to,
                                                            StatisticsGroupBy groupBy, long pendingCount,
                                                            BigDecimal pendingAmount, long requestedCount,
                                                            BigDecimal requestedAmount, long completedCount,
                                                            BigDecimal completedAmount, long rejectedCount,
                                                            BigDecimal rejectedAmount, Long avgProcessingMinutes,
                                                            List<WithdrawalOwnerTotalResponse> byOwnerType,
                                                            List<WithdrawalSeriesPointResponse> series);
}
