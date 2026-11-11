package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.request.WalletPosting;
import mss301.paymentservice.dto.response.WalletCenterBalanceResponse;
import mss301.paymentservice.dto.response.WalletResponse;
import mss301.paymentservice.dto.response.WalletSeriesPointResponse;
import mss301.paymentservice.dto.response.WalletStatisticsResponse;
import mss301.paymentservice.dto.response.WalletTransactionResponse;
import mss301.paymentservice.dto.response.WalletTypeTotalResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface WalletMapper {

    // Ví mới luôn bắt đầu từ 0 — số dư chỉ đổi qua WalletService.post.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "availableBalance", constant = "0")
    @Mapping(target = "heldBalance", constant = "0")
    Wallet toWallet(WalletOwnerType ownerType, UUID ownerId);

    @Mapping(target = "id", ignore = true)
    WalletTransaction toTransaction(WalletPosting posting, UUID walletId, BigDecimal availableBefore,
                                    BigDecimal availableAfter, BigDecimal heldBefore, BigDecimal heldAfter,
                                    Instant createdAt);

    WalletResponse toResponse(Wallet wallet, boolean withdrawable);

    // Chủ chưa có ví (chưa có biến động nào): số dư 0.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "availableBalance", constant = "0")
    @Mapping(target = "heldBalance", constant = "0")
    WalletResponse toEmptyResponse(WalletOwnerType ownerType, UUID ownerId, boolean withdrawable);

    WalletTransactionResponse toTransactionResponse(WalletTransaction transaction);

    List<WalletTransactionResponse> toTransactionResponseList(List<WalletTransaction> transactions);

    WalletCenterBalanceResponse toCenterBalanceResponse(UUID centerId, BigDecimal availableBalance);

    WalletSeriesPointResponse toSeriesPoint(String period, BigDecimal income, BigDecimal withdrawn, BigDecimal spent);

    // Phần chung của dashboard; số riêng theo loại chủ ví do service gán sau (bỏ khỏi JSON khi null).
    @Mapping(target = "totalPaid", ignore = true)
    @Mapping(target = "refundedToWallet", ignore = true)
    @Mapping(target = "refundedToGateway", ignore = true)
    @Mapping(target = "netSpent", ignore = true)
    @Mapping(target = "forfeitIncome", ignore = true)
    @Mapping(target = "serviceIncome", ignore = true)
    @Mapping(target = "commissionDeducted", ignore = true)
    @Mapping(target = "complaintDebits", ignore = true)
    @Mapping(target = "commissionIncome", ignore = true)
    @Mapping(target = "grossCollected", ignore = true)
    @Mapping(target = "totalRefunded", ignore = true)
    @Mapping(target = "liabilities", ignore = true)
    @Mapping(target = "pendingWithdrawalCount", ignore = true)
    @Mapping(target = "pendingWithdrawalAmount", ignore = true)
    WalletStatisticsResponse toStatisticsResponse(WalletOwnerType ownerType, Instant from, Instant to,
                                                  StatisticsGroupBy groupBy, BigDecimal availableBalance,
                                                  BigDecimal heldBalance, BigDecimal totalIncome,
                                                  BigDecimal totalWithdrawn, Long bookingCount,
                                                  List<WalletTypeTotalResponse> byType,
                                                  List<WalletSeriesPointResponse> series);
}
