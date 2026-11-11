package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
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
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.WalletWithdrawalService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet-withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Wallet Withdrawals", description = "Yêu cầu rút tiền từ ví: chủ ví gửi (tiền bị tạm giữ); khách / caregiver do trung tâm giữ khoản tiền xử lý, ví trung tâm do admin xử lý — chuyển khoản giả lập (tự hoàn tất) hoặc từ chối (hoàn số dư)")
public class WalletWithdrawalController {

    WalletWithdrawalService walletWithdrawalService;

    // API danh sách yêu cầu rút tiền theo phạm vi người gọi.
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<PageResponse<WalletWithdrawalResponse>> list(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam(defaultValue = "OWN") WalletWithdrawalScope scope,
            @RequestParam(required = false) WalletWithdrawalStatus status,
            @RequestParam(required = false) WalletOwnerType ownerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletWithdrawalService.list(actorId, roles, claimCenterId, scope, status, ownerType, page, size);
    }

    // API thống kê yêu cầu rút tiền.
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletWithdrawalStatisticsResponse> getStatistics(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam(defaultValue = "OWN") WalletWithdrawalScope scope,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "DAY") StatisticsGroupBy groupBy) {
        return walletWithdrawalService.getStatistics(actorId, roles, claimCenterId, scope, from, to, groupBy);
    }

    // API xem chi tiết một yêu cầu rút tiền.
    @GetMapping("/{withdrawalId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletWithdrawalResponse> get(@CurrentUserId UUID actorId,
                                                     @CurrentRoles Set<RoleCode> roles,
                                                     @CurrentCenterId(required = false) UUID claimCenterId,
                                                     @PathVariable UUID withdrawalId) {
        return walletWithdrawalService.get(actorId, roles, claimCenterId, withdrawalId);
    }

    // 2026-11-11: tiền buổi vào thẳng tài khoản trung tâm / nền tảng → chỉ khách và caregiver gửi yêu cầu rút.
    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER')")
    public APIResponse<WalletWithdrawalResponse> create(@CurrentUserId UUID actorId,
                                                        @CurrentRoles Set<RoleCode> roles,
                                                        @CurrentCenterId(required = false) UUID claimCenterId,
                                                        @Valid @RequestBody CreateWalletWithdrawalRequest request) {
        return walletWithdrawalService.create(actorId, roles, claimCenterId, request);
    }

    // Chuyển khoản giả lập; thành công thì yêu cầu tự chuyển COMPLETED (không đổi trạng thái tay).
    @PostMapping("/{withdrawalId}/transfers")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletWithdrawalResponse> transfer(@CurrentUserId UUID actorId,
                                                          @CurrentRoles Set<RoleCode> roles,
                                                          @CurrentCenterId(required = false) UUID claimCenterId,
                                                          @PathVariable UUID withdrawalId) {
        return walletWithdrawalService.transfer(actorId, roles, claimCenterId, withdrawalId);
    }

    // API từ chối yêu cầu rút tiền.
    @PostMapping("/{withdrawalId}/reject")
    @PreAuthorize("hasAnyRole('CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletWithdrawalResponse> reject(@CurrentUserId UUID actorId,
                                                        @CurrentRoles Set<RoleCode> roles,
                                                        @CurrentCenterId(required = false) UUID claimCenterId,
                                                        @PathVariable UUID withdrawalId,
                                                        @Valid @RequestBody RejectWalletWithdrawalRequest request) {
        return walletWithdrawalService.reject(actorId, roles, claimCenterId, withdrawalId, request);
    }
}
