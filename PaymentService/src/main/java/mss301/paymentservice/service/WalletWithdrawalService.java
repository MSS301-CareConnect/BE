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

/**
 * Yêu cầu rút tiền. Từ 2026-11-10: yêu cầu của khách / caregiver gắn trung tâm đã tạo ra khoản tiền và do quản lý trung
 * tâm đó xử lý; yêu cầu của ví trung tâm (và yêu cầu cũ không gắn trung tâm) do admin xử lý. Không còn duyệt / xác nhận
 * thủ công: người xử lý chạy chuyển khoản giả lập, thành công thì yêu cầu tự hoàn tất; hoặc từ chối kèm lý do. Người rút
 * nhận thông báo kết quả.
 */
public interface WalletWithdrawalService {

    APIResponse<PageResponse<WalletWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                             WalletWithdrawalScope scope,
                                                             WalletWithdrawalStatus status,
                                                             WalletOwnerType ownerType, int page, int size);

    APIResponse<WalletWithdrawalStatisticsResponse> getStatistics(UUID actorId, Set<RoleCode> roles,
                                                                  UUID claimCenterId, WalletWithdrawalScope scope,
                                                                  Instant from, Instant to,
                                                                  StatisticsGroupBy groupBy);

    APIResponse<WalletWithdrawalResponse> get(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                              UUID withdrawalId);

    APIResponse<WalletWithdrawalResponse> create(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                 CreateWalletWithdrawalRequest request);

    APIResponse<WalletWithdrawalResponse> transfer(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                   UUID withdrawalId);

    APIResponse<WalletWithdrawalResponse> reject(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                 UUID withdrawalId, RejectWalletWithdrawalRequest request);
}
