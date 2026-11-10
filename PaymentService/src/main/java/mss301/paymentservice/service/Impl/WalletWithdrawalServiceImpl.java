package mss301.paymentservice.service.Impl;

import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.request.CompleteWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.RejectWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.WalletPosting;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.WalletWithdrawalMapper;
import mss301.paymentservice.repository.WalletWithdrawalRepository;
import mss301.paymentservice.service.WalletService;
import mss301.paymentservice.service.WalletWithdrawalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WalletWithdrawalServiceImpl implements WalletWithdrawalService {
    static int MAX_PAGE_SIZE = 100;
    static String CREATED_AT = "createdAt";
    static String HOLD_KEY = "WD_HOLD:";
    static String RELEASE_KEY = "WD_RELEASE:";
    static String PAID_KEY = "WD_PAID:";
    static Set<WalletWithdrawalStatus> REJECTABLE_STATUSES = EnumSet.of(WalletWithdrawalStatus.PENDING,
            WalletWithdrawalStatus.APPROVED);

    WalletWithdrawalRepository walletWithdrawalRepository;
    WalletService walletService;
    WalletWithdrawalMapper walletWithdrawalMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WalletWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                    UUID claimCenterId, WalletWithdrawalStatus status,
                                                                    WalletOwnerType ownerType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, CREATED_AT));
        UUID scopeWalletId = null;
        if (!roles.contains(RoleCode.ADMIN)) {
            Wallet wallet = walletService.findOwnWallet(actorId, roles, claimCenterId).orElse(null);
            if (wallet == null) {
                PageResponse<WalletWithdrawalResponse> emptyPage = PageResponse.of(Page.empty(pageRequest), List.of());
                return APIResponse.success("Lấy danh sách yêu cầu rút tiền thành công", emptyPage);
            }
            scopeWalletId = wallet.getId();
        }
        UUID walletId = scopeWalletId;
        Specification<WalletWithdrawal> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (walletId != null) {
                predicates.add(criteriaBuilder.equal(root.get("walletId"), walletId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (ownerType != null) {
                predicates.add(criteriaBuilder.equal(root.get("ownerType"), ownerType));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<WalletWithdrawal> withdrawals = walletWithdrawalRepository.findAll(specification, pageRequest);
        PageResponse<WalletWithdrawalResponse> pageResponse = PageResponse.of(withdrawals,
                walletWithdrawalMapper.toResponseList(withdrawals.getContent()));
        return APIResponse.success("Lấy danh sách yêu cầu rút tiền thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletWithdrawalResponse> get(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                     UUID withdrawalId) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        if (!roles.contains(RoleCode.ADMIN)) {
            // Yêu cầu của ví khác → 404 (không lộ là có tồn tại).
            boolean own = walletService.findOwnWallet(actorId, roles, claimCenterId)
                    .map(wallet -> wallet.getId().equals(withdrawal.getWalletId()))
                    .orElse(false);
            if (!own) {
                throw new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND);
            }
        }
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Lấy yêu cầu rút tiền thành công", withdrawalResponse);
    }

    // Tạm giữ ngay lúc tạo: số khả dụng giảm, yêu cầu khác không rút được phần đang chờ duyệt.
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> create(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                        CreateWalletWithdrawalRequest request) {
        Wallet wallet = walletService.lockOwnWallet(actorId, roles, claimCenterId);
        if (request.getAmount().compareTo(wallet.getAvailableBalance()) > 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }
        UUID centerId = wallet.getOwnerType() == WalletOwnerType.CENTER ? wallet.getOwnerId() : null;
        WalletWithdrawal withdrawal = walletWithdrawalRepository.saveAndFlush(walletWithdrawalMapper.toWithdrawal(
                request, wallet, actorId, centerId, WalletWithdrawalStatus.PENDING));
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_HOLD, withdrawal.getAmount(),
                HOLD_KEY + withdrawal.getId(), centerId, null, null, null, withdrawal.getId(), null));
        log.info("Withdrawal {} of {} requested from wallet {} ({})", withdrawal.getId(), withdrawal.getAmount(),
                wallet.getId(), wallet.getOwnerType());
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã gửi yêu cầu rút tiền", withdrawalResponse);
    }

    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> approve(UUID actorId, UUID withdrawalId) {
        WalletWithdrawal withdrawal = findForUpdate(withdrawalId);
        if (withdrawal.getStatus() != WalletWithdrawalStatus.PENDING) {
            throw new AppException(ErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        withdrawal.setStatus(WalletWithdrawalStatus.APPROVED);
        withdrawal.setReviewedBy(actorId);
        withdrawal.setReviewedAt(clock.instant());
        log.info("Withdrawal {} approved by admin {}", withdrawalId, actorId);
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã duyệt yêu cầu rút tiền", withdrawalResponse);
    }

    // Từ chối (kể cả sau khi đã duyệt nhưng chưa chuyển): trả phần tạm giữ về số dư khả dụng.
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> reject(UUID actorId, UUID withdrawalId,
                                                        RejectWalletWithdrawalRequest request) {
        WalletWithdrawal withdrawal = findForUpdate(withdrawalId);
        if (!REJECTABLE_STATUSES.contains(withdrawal.getStatus())) {
            throw new AppException(ErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        Wallet wallet = walletService.lockWallet(withdrawal.getWalletId());
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_RELEASE, withdrawal.getAmount(),
                RELEASE_KEY + withdrawalId, withdrawal.getCenterId(), null, null, null, withdrawalId, null));
        Instant now = clock.instant();
        withdrawal.setStatus(WalletWithdrawalStatus.REJECTED);
        withdrawal.setRejectReason(request.getReason().trim());
        withdrawal.setReviewedBy(actorId);
        withdrawal.setReviewedAt(now);
        log.info("Withdrawal {} rejected by admin {}", withdrawalId, actorId);
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã từ chối yêu cầu rút tiền", withdrawalResponse);
    }

    // Admin đã chuyển khoản thủ công: tiền rời ví (phần tạm giữ giảm).
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> complete(UUID actorId, UUID withdrawalId,
                                                          CompleteWalletWithdrawalRequest request) {
        WalletWithdrawal withdrawal = findForUpdate(withdrawalId);
        if (withdrawal.getStatus() != WalletWithdrawalStatus.APPROVED) {
            throw new AppException(ErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        Wallet wallet = walletService.lockWallet(withdrawal.getWalletId());
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_PAID, withdrawal.getAmount(),
                PAID_KEY + withdrawalId, withdrawal.getCenterId(), null, null, null, withdrawalId,
                request.getTransferReference().trim()));
        withdrawal.setStatus(WalletWithdrawalStatus.COMPLETED);
        withdrawal.setTransferReference(request.getTransferReference().trim());
        withdrawal.setCompletedBy(actorId);
        withdrawal.setCompletedAt(clock.instant());
        log.info("Withdrawal {} completed by admin {}", withdrawalId, actorId);
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã xác nhận chuyển khoản", withdrawalResponse);
    }

    private WalletWithdrawal findForUpdate(UUID withdrawalId) {
        return walletWithdrawalRepository.findForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
    }
}
