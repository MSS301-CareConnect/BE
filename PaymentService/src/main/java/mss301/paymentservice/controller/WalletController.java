package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletCenterBalanceResponse;
import mss301.paymentservice.dto.response.WalletResponse;
import mss301.paymentservice.dto.response.WalletStatisticsResponse;
import mss301.paymentservice.dto.response.WalletTransactionResponse;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.WalletService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Wallets", description = "Ví: khách (tiền hoàn), caregiver (cọc được hưởng), trung tâm (tiền buổi sau hoa hồng), nền tảng (hoa hồng, chỉ xem) — số dư, lịch sử giao dịch, dashboard")
public class WalletController {

    WalletService walletService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletResponse> getMine(@CurrentUserId UUID actorId,
                                               @CurrentRoles Set<RoleCode> roles,
                                               @CurrentCenterId(required = false) UUID claimCenterId) {
        return walletService.getMine(actorId, roles, claimCenterId);
    }

    @GetMapping("/me/transactions")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<PageResponse<WalletTransactionResponse>> listMyTransactions(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletService.listMyTransactions(actorId, roles, claimCenterId, type, from, to, page, size);
    }

    // 2026-11-10: số dư theo trung tâm giữ khoản tiền — chọn trung tâm xử lý yêu cầu rút.
    @GetMapping("/me/center-balances")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER')")
    public APIResponse<List<WalletCenterBalanceResponse>> listMyCenterBalances(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId) {
        return walletService.listMyCenterBalances(actorId, roles, claimCenterId);
    }

    @GetMapping("/me/statistics")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletStatisticsResponse> getMyStatistics(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) StatisticsGroupBy groupBy) {
        return walletService.getMyStatistics(actorId, roles, claimCenterId, from, to, groupBy);
    }

    @GetMapping("/{walletId}")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<WalletResponse> get(@PathVariable UUID walletId) {
        return walletService.get(walletId);
    }

    @GetMapping("/{walletId}/transactions")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<PageResponse<WalletTransactionResponse>> listTransactions(
            @PathVariable UUID walletId,
            @RequestParam(required = false) WalletTransactionType type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletService.listTransactions(walletId, type, from, to, page, size);
    }
}
