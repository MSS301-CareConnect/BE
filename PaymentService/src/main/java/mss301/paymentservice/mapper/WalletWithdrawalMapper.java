package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface WalletWithdrawalMapper {

    // Ví, người yêu cầu, trung tâm, trạng thái do service quyết định; phần duyệt / chuyển khoản trống tới khi admin xử lý.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "walletId", source = "wallet.id")
    @Mapping(target = "ownerType", source = "wallet.ownerType")
    @Mapping(target = "rejectReason", ignore = true)
    @Mapping(target = "reviewedBy", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "transferReference", ignore = true)
    @Mapping(target = "completedBy", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    WalletWithdrawal toWithdrawal(CreateWalletWithdrawalRequest request, Wallet wallet, UUID requesterUserId,
                                  UUID centerId, WalletWithdrawalStatus status);

    WalletWithdrawalResponse toResponse(WalletWithdrawal withdrawal);

    List<WalletWithdrawalResponse> toResponseList(List<WalletWithdrawal> withdrawals);
}
