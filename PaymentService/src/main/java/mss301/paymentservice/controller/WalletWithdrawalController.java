package mss301.paymentservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.dto.request.CompleteWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.RejectWalletWithdrawalRequest;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import mss301.paymentservice.service.WalletWithdrawalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet-withdrawals")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Wallet Withdrawals", description = "Yêu cầu rút tiền từ ví: chủ ví gửi (tiền bị tạm giữ), admin duyệt / từ chối (hoàn số dư) / xác nhận đã chuyển khoản")
public class WalletWithdrawalController {

    WalletWithdrawalService walletWithdrawalService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<PageResponse<WalletWithdrawalResponse>> list(
            @CurrentUserId UUID actorId,
            @CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID claimCenterId,
            @RequestParam(required = false) WalletWithdrawalStatus status,
            @RequestParam(required = false) WalletOwnerType ownerType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return walletWithdrawalService.list(actorId, roles, claimCenterId, status, ownerType, page, size);
    }

    @GetMapping("/{withdrawalId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER','ADMIN')")
    public APIResponse<WalletWithdrawalResponse> get(@CurrentUserId UUID actorId,
                                                     @CurrentRoles Set<RoleCode> roles,
                                                     @CurrentCenterId(required = false) UUID claimCenterId,
                                                     @PathVariable UUID withdrawalId) {
        return walletWithdrawalService.get(actorId, roles, claimCenterId, withdrawalId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','CAREGIVER','CENTER_MANAGER')")
    public APIResponse<WalletWithdrawalResponse> create(@CurrentUserId UUID actorId,
                                                        @CurrentRoles Set<RoleCode> roles,
                                                        @CurrentCenterId(required = false) UUID claimCenterId,
                                                        @Valid @RequestBody CreateWalletWithdrawalRequest request) {
        return walletWithdrawalService.create(actorId, roles, claimCenterId, request);
    }

    @PostMapping("/{withdrawalId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<WalletWithdrawalResponse> approve(@CurrentUserId UUID actorId, @PathVariable UUID withdrawalId) {
        return walletWithdrawalService.approve(actorId, withdrawalId);
    }

    @PostMapping("/{withdrawalId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<WalletWithdrawalResponse> reject(@CurrentUserId UUID actorId,
                                                        @PathVariable UUID withdrawalId,
                                                        @Valid @RequestBody RejectWalletWithdrawalRequest request) {
        return walletWithdrawalService.reject(actorId, withdrawalId, request);
    }

    @PostMapping("/{withdrawalId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<WalletWithdrawalResponse> complete(@CurrentUserId UUID actorId,
                                                          @PathVariable UUID withdrawalId,
                                                          @Valid @RequestBody CompleteWalletWithdrawalRequest request) {
        return walletWithdrawalService.complete(actorId, withdrawalId, request);
    }
}
