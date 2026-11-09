package mss301.paymentservice.service;

import mss301.paymentservice.dto.request.CompleteWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.RejectWalletWithdrawalRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;

import java.util.Set;
import java.util.UUID;

public interface WalletWithdrawalService {

    APIResponse<PageResponse<WalletWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                             WalletWithdrawalStatus status, WalletOwnerType ownerType,
                                                             int page, int size);

    APIResponse<WalletWithdrawalResponse> get(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                              UUID withdrawalId);

    APIResponse<WalletWithdrawalResponse> create(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                 CreateWalletWithdrawalRequest request);

    APIResponse<WalletWithdrawalResponse> approve(UUID actorId, UUID withdrawalId);

    APIResponse<WalletWithdrawalResponse> reject(UUID actorId, UUID withdrawalId, RejectWalletWithdrawalRequest request);

    APIResponse<WalletWithdrawalResponse> complete(UUID actorId, UUID withdrawalId,
                                                   CompleteWalletWithdrawalRequest request);
}
