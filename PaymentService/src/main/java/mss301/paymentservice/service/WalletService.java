package mss301.paymentservice.service;

import mss301.paymentservice.dto.request.WalletPosting;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletCenterBalanceResponse;
import mss301.paymentservice.dto.response.WalletResponse;
import mss301.paymentservice.dto.response.WalletStatisticsResponse;
import mss301.paymentservice.dto.response.WalletTransactionResponse;
import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletTransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WalletService {

    APIResponse<WalletResponse> getMine(UUID actorId, Set<RoleCode> roles, UUID claimCenterId);

    APIResponse<WalletResponse> get(UUID walletId);

    APIResponse<PageResponse<WalletTransactionResponse>> listMyTransactions(UUID actorId, Set<RoleCode> roles,
                                                                            UUID claimCenterId,
                                                                            WalletTransactionType type, Instant from,
                                                                            Instant to, int page, int size);

    APIResponse<PageResponse<WalletTransactionResponse>> listTransactions(UUID walletId, WalletTransactionType type,
                                                                          Instant from, Instant to, int page,
                                                                          int size);

    // 2026-11-10: số dư khả dụng của ví khách / caregiver theo trung tâm giữ khoản tiền (chọn nơi gửi yêu cầu rút).
    APIResponse<List<WalletCenterBalanceResponse>> listMyCenterBalances(UUID actorId, Set<RoleCode> roles,
                                                                       UUID claimCenterId);

    APIResponse<WalletStatisticsResponse> getMyStatistics(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                          Instant from, Instant to, StatisticsGroupBy groupBy);

    // ===== ghi sổ — MANDATORY: luôn cùng transaction với sự kiện tiền gây ra biến động =====

    // Hoàn tiền kênh WALLET đã xong (dispatcher) → cộng ví khách.
    void creditRefund(Refund refund);

    // Cọc bị giữ của một buổi → ví caregiver được giao, chưa có người thì ví trung tâm.
    void creditForfeiture(DepositForfeiture forfeiture);

    // Buổi hoàn tất / khách vắng: phần trung tâm vào ví trung tâm, hoa hồng vào ví nền tảng.
    void settleOccurrence(OccurrenceFinancial occurrenceFinancial, BigDecimal centerAmount, BigDecimal commission);

    // Hoàn khiếu nại sau khi booking đã được cộng cho trung tâm → trừ ví trung tâm (trần = đã cộng − đã trừ của booking).
    // Trả về số đã trừ (0 khi booking chưa cộng gì cho trung tâm).
    BigDecimal debitComplaintRefund(UUID centerId, UUID bookingId, UUID complaintId, BigDecimal amount);

    // Phần hoàn khiếu nại không còn ai gánh (trung tâm đã trừ hết, không còn buổi chưa đối soát) là hoa hồng nền tảng đã thu
    // trên tiền vừa hoàn → trừ lại ví nền tảng (trần = hoa hồng đã thu − đã trừ của booking).
    void debitComplaintCommission(UUID centerId, UUID bookingId, UUID complaintId, BigDecimal amount);

    // Ví của chính người gọi, đã khóa (tạo nếu chưa có) — ví trung tâm / nền tảng không rút → WALLET_NOT_WITHDRAWABLE.
    Wallet lockOwnWallet(UUID actorId, Set<RoleCode> roles, UUID claimCenterId);

    Optional<Wallet> findOwnWallet(UUID actorId, Set<RoleCode> roles, UUID claimCenterId);

    Wallet lockWallet(UUID walletId);

    // Số dư khả dụng của một ví theo center_id của biến động (key null = tiền không gắn trung tâm); chỉ số dương.
    Map<UUID, BigDecimal> findCenterBalances(UUID walletId);

    // false = referenceKey đã ghi (sự kiện xử lý lại) — không đổi số dư.
    boolean post(Wallet wallet, WalletPosting posting);
}
