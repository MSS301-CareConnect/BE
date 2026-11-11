package mss301.paymentservice.service;

import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.RejectWalletWithdrawalRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalStatisticsResponse;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalScope;
import mss301.paymentservice.enums.WalletWithdrawalStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface WalletWithdrawalService {

    // Danh sách yêu cầu rút tiền.
    APIResponse<PageResponse<WalletWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                             WalletWithdrawalScope scope,
                                                             WalletWithdrawalStatus status,
                                                             WalletOwnerType ownerType, int page, int size);

    // Thống kê yêu cầu rút tiền.
    APIResponse<WalletWithdrawalStatisticsResponse> getStatistics(UUID actorId, Set<RoleCode> roles,
                                                                  UUID claimCenterId, WalletWithdrawalScope scope,
                                                                  Instant from, Instant to,
                                                                  StatisticsGroupBy groupBy);

    // Chi tiết yêu cầu rút tiền.
    APIResponse<WalletWithdrawalResponse> get(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                              UUID withdrawalId);

    // Tạo yêu cầu rút tiền.
    APIResponse<WalletWithdrawalResponse> create(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                 CreateWalletWithdrawalRequest request);

    // Xác nhận đã chuyển tiền cho yêu cầu rút.
    APIResponse<WalletWithdrawalResponse> transfer(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                   UUID withdrawalId);

    // Từ chối yêu cầu rút tiền.
    APIResponse<WalletWithdrawalResponse> reject(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                 UUID withdrawalId, RejectWalletWithdrawalRequest request);
}
