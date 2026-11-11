package mss301.paymentservice.service.Impl;

import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.request.CreateWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.RejectWalletWithdrawalRequest;
import mss301.paymentservice.dto.request.WalletPosting;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalResponse;
import mss301.paymentservice.dto.response.WalletWithdrawalStatisticsResponse;
import mss301.paymentservice.dto.response.WithdrawalOwnerTotalResponse;
import mss301.paymentservice.dto.response.WithdrawalSeriesPointResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.enums.WalletWithdrawalScope;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.WalletWithdrawalMapper;
import mss301.paymentservice.repository.WalletCenterTypeTotalView;
import mss301.paymentservice.repository.WalletTransactionRepository;
import mss301.paymentservice.repository.WalletWithdrawalRepository;
import mss301.paymentservice.service.PaymentOutboxService;
import mss301.paymentservice.service.WalletService;
import mss301.paymentservice.service.WalletWithdrawalService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WalletWithdrawalServiceImpl implements WalletWithdrawalService {
    static int MAX_PAGE_SIZE = 100;
    static int MAX_SERIES_POINTS = 400;
    static String CREATED_AT = "createdAt";
    static String HOLD_KEY = "WD_HOLD:";
    static String RELEASE_KEY = "WD_RELEASE:";
    static String PAID_KEY = "WD_PAID:";
    // Mã chuyển khoản giả lập: SIM + giờ VN + 6 ký tự đầu của mã yêu cầu (đủ duy nhất, dễ đối chiếu).
    static String SIMULATED_REFERENCE_PREFIX = "SIM";
    static int SIMULATED_REFERENCE_SUFFIX_LENGTH = 6;
    static ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static DateTimeFormatter REFERENCE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    static DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    // Yêu cầu cũ có thể đang APPROVED (luồng duyệt tay trước 2026-11-10) — vẫn chuyển / từ chối được.
    static Set<WalletWithdrawalStatus> OPEN_STATUSES = EnumSet.of(WalletWithdrawalStatus.PENDING,
            WalletWithdrawalStatus.APPROVED);
    static Set<WalletOwnerType> CENTER_ROUTED_OWNERS = EnumSet.of(WalletOwnerType.CUSTOMER,
            WalletOwnerType.CAREGIVER);

    WalletWithdrawalRepository walletWithdrawalRepository;
    WalletTransactionRepository walletTransactionRepository;
    WalletService walletService;
    PaymentOutboxService paymentOutboxService;
    WalletWithdrawalMapper walletWithdrawalMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WalletWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                    UUID claimCenterId, WalletWithdrawalScope scope,
                                                                    WalletWithdrawalStatus status,
                                                                    WalletOwnerType ownerType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, CREATED_AT));
        Optional<Specification<WalletWithdrawal>> scopeSpecification = buildScope(actorId, roles, claimCenterId,
                scope);
        if (scopeSpecification.isEmpty()) {
            PageResponse<WalletWithdrawalResponse> emptyPage = PageResponse.of(Page.empty(pageRequest), List.of());
            return APIResponse.success("Lấy danh sách yêu cầu rút tiền thành công", emptyPage);
        }
        Specification<WalletWithdrawal> filters = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (ownerType != null) {
                predicates.add(criteriaBuilder.equal(root.get("ownerType"), ownerType));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<WalletWithdrawal> withdrawals = walletWithdrawalRepository.findAll(
                scopeSpecification.get().and(filters), pageRequest);
        PageResponse<WalletWithdrawalResponse> pageResponse = PageResponse.of(withdrawals,
                walletWithdrawalMapper.toResponseList(withdrawals.getContent()));
        return APIResponse.success("Lấy danh sách yêu cầu rút tiền thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletWithdrawalStatisticsResponse> getStatistics(UUID actorId, Set<RoleCode> roles,
                                                                         UUID claimCenterId,
                                                                         WalletWithdrawalScope scope, Instant from,
                                                                         Instant to, StatisticsGroupBy groupBy) {
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Map<String, BigDecimal[]> buckets = createBuckets(from, to, groupBy);
        List<WalletWithdrawal> open = new ArrayList<>();
        List<WalletWithdrawal> touched = new ArrayList<>();
        Optional<Specification<WalletWithdrawal>> scopeSpecification = buildScope(actorId, roles, claimCenterId,
                scope);
        if (scopeSpecification.isPresent()) {
            open = walletWithdrawalRepository.findAll(scopeSpecification.get().and(hasStatusIn(OPEN_STATUSES)));
            touched = walletWithdrawalRepository.findAll(scopeSpecification.get().and(touchedBetween(from, to)));
        }
        Map<WalletOwnerType, OwnerTotals> owners = new LinkedHashMap<>();
        BigDecimal pendingAmount = BigDecimal.ZERO;
        for (WalletWithdrawal withdrawal : open) {
            pendingAmount = pendingAmount.add(withdrawal.getAmount());
            OwnerTotals totals = owners.computeIfAbsent(withdrawal.getOwnerType(), type -> new OwnerTotals());
            totals.pendingCount++;
            totals.pendingAmount = totals.pendingAmount.add(withdrawal.getAmount());
        }
        PeriodTotals period = new PeriodTotals();
        for (WalletWithdrawal withdrawal : touched) {
            if (isWithin(withdrawal.getCreatedAt(), from, to)) {
                period.requestedCount++;
                period.requestedAmount = period.requestedAmount.add(withdrawal.getAmount());
                addToBucket(buckets, withdrawal.getCreatedAt(), groupBy, 0, withdrawal.getAmount());
            }
            if (withdrawal.getStatus() == WalletWithdrawalStatus.COMPLETED
                    && isWithin(withdrawal.getCompletedAt(), from, to)) {
                period.completedCount++;
                period.completedAmount = period.completedAmount.add(withdrawal.getAmount());
                period.processingMinutes += Duration.between(withdrawal.getCreatedAt(),
                        withdrawal.getCompletedAt()).toMinutes();
                addToBucket(buckets, withdrawal.getCompletedAt(), groupBy, 1, withdrawal.getAmount());
                OwnerTotals totals = owners.computeIfAbsent(withdrawal.getOwnerType(), type -> new OwnerTotals());
                totals.completedCount++;
                totals.completedAmount = totals.completedAmount.add(withdrawal.getAmount());
            }
            if (withdrawal.getStatus() == WalletWithdrawalStatus.REJECTED
                    && isWithin(withdrawal.getReviewedAt(), from, to)) {
                period.rejectedCount++;
                period.rejectedAmount = period.rejectedAmount.add(withdrawal.getAmount());
            }
        }
        Long avgProcessingMinutes = period.completedCount == 0
                ? null
                : Math.round((double) period.processingMinutes / period.completedCount);
        List<WithdrawalOwnerTotalResponse> byOwnerType = owners.entrySet().stream()
                .map(entry -> walletWithdrawalMapper.toOwnerTotal(entry.getKey(), entry.getValue().pendingCount,
                        entry.getValue().pendingAmount, entry.getValue().completedCount,
                        entry.getValue().completedAmount))
                .toList();
        List<WithdrawalSeriesPointResponse> series = buckets.entrySet().stream()
                .map(entry -> walletWithdrawalMapper.toSeriesPoint(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1]))
                .toList();
        WalletWithdrawalStatisticsResponse statisticsResponse = walletWithdrawalMapper.toStatisticsResponse(scope,
                from, to, groupBy, open.size(), pendingAmount, period.requestedCount, period.requestedAmount,
                period.completedCount, period.completedAmount, period.rejectedCount, period.rejectedAmount,
                avgProcessingMinutes, byOwnerType, series);
        if (scope == WalletWithdrawalScope.REVIEW && isManagerOnly(roles)) {
            fillCustody(statisticsResponse, claimCenterId);
        }
        return APIResponse.success("Thống kê yêu cầu rút tiền thành công", statisticsResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletWithdrawalResponse> get(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                     UUID withdrawalId) {
        WalletWithdrawal withdrawal = walletWithdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
        // Yêu cầu ngoài phạm vi → 404 (không lộ là có tồn tại).
        if (!roles.contains(RoleCode.ADMIN) && !isReviewer(roles, claimCenterId, withdrawal)) {
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

    // Tạm giữ ngay lúc tạo: số khả dụng giảm, yêu cầu khác không rút được phần đang chờ xử lý.
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> create(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                        CreateWalletWithdrawalRequest request) {
        Wallet wallet = walletService.lockOwnWallet(actorId, roles, claimCenterId);
        if (request.getAmount().compareTo(wallet.getAvailableBalance()) > 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }
        UUID centerId = wallet.getOwnerType() == WalletOwnerType.CENTER
                ? wallet.getOwnerId()
                : chooseSourceCenter(wallet, request);
        WalletWithdrawal withdrawal = walletWithdrawalRepository.saveAndFlush(walletWithdrawalMapper.toWithdrawal(
                request, wallet, actorId, centerId, WalletWithdrawalStatus.PENDING));
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_HOLD, withdrawal.getAmount(),
                HOLD_KEY + withdrawal.getId(), centerId, null, null, null, withdrawal.getId(), null));
        log.info("Withdrawal {} of {} requested from wallet {} ({}), handled by {}", withdrawal.getId(),
                withdrawal.getAmount(), wallet.getId(), wallet.getOwnerType(),
                isCenterRouted(withdrawal) ? "center " + centerId : "admin");
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã gửi yêu cầu rút tiền", withdrawalResponse);
    }

    // Chuyển khoản giả lập: thành công thì yêu cầu tự hoàn tất (không còn bước duyệt / xác nhận tay), tiền rời ví và
    // người rút được báo.
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> transfer(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                          UUID withdrawalId) {
        WalletWithdrawal withdrawal = findForUpdate(withdrawalId);
        checkReviewer(roles, claimCenterId, withdrawal);
        if (!OPEN_STATUSES.contains(withdrawal.getStatus())) {
            throw new AppException(ErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        Instant now = clock.instant();
        String transferReference = simulateBankTransfer(withdrawal, now);
        Wallet wallet = walletService.lockWallet(withdrawal.getWalletId());
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_PAID, withdrawal.getAmount(),
                PAID_KEY + withdrawalId, withdrawal.getCenterId(), null, null, null, withdrawalId,
                transferReference));
        withdrawal.setStatus(WalletWithdrawalStatus.COMPLETED);
        withdrawal.setTransferReference(transferReference);
        if (withdrawal.getReviewedAt() == null) {
            withdrawal.setReviewedBy(actorId);
            withdrawal.setReviewedAt(now);
        }
        withdrawal.setCompletedBy(actorId);
        withdrawal.setCompletedAt(now);
        paymentOutboxService.enqueueWithdrawalResult(withdrawal);
        log.info("Withdrawal {} transferred ({}) by {}", withdrawalId, transferReference, actorId);
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Chuyển khoản thành công, yêu cầu rút tiền đã hoàn tất", withdrawalResponse);
    }

    // Từ chối: trả phần tạm giữ về số dư khả dụng và báo người rút lý do.
    @Override
    @Transactional
    public APIResponse<WalletWithdrawalResponse> reject(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                        UUID withdrawalId, RejectWalletWithdrawalRequest request) {
        WalletWithdrawal withdrawal = findForUpdate(withdrawalId);
        checkReviewer(roles, claimCenterId, withdrawal);
        if (!OPEN_STATUSES.contains(withdrawal.getStatus())) {
            throw new AppException(ErrorCode.WITHDRAWAL_STATUS_INVALID);
        }
        Wallet wallet = walletService.lockWallet(withdrawal.getWalletId());
        walletService.post(wallet, new WalletPosting(WalletTransactionType.WITHDRAWAL_RELEASE, withdrawal.getAmount(),
                RELEASE_KEY + withdrawalId, withdrawal.getCenterId(), null, null, null, withdrawalId, null));
        withdrawal.setStatus(WalletWithdrawalStatus.REJECTED);
        withdrawal.setRejectReason(request.getReason().trim());
        withdrawal.setReviewedBy(actorId);
        withdrawal.setReviewedAt(clock.instant());
        paymentOutboxService.enqueueWithdrawalResult(withdrawal);
        log.info("Withdrawal {} rejected by {}", withdrawalId, actorId);
        WalletWithdrawalResponse withdrawalResponse = walletWithdrawalMapper.toResponse(withdrawal);
        return APIResponse.success("Đã từ chối yêu cầu rút tiền", withdrawalResponse);
    }

    private WalletWithdrawal findForUpdate(UUID withdrawalId) {
        return walletWithdrawalRepository.findForUpdate(withdrawalId)
                .orElseThrow(() -> new AppException(ErrorCode.WITHDRAWAL_NOT_FOUND));
    }

    // Khách / caregiver rút phần tiền của trung tâm nào thì trung tâm đó xử lý. Bỏ trống: phần không gắn trung tâm (dữ liệu
    // cũ, admin xử lý) nếu đủ, không thì trung tâm duy nhất còn số dư.
    private UUID chooseSourceCenter(Wallet wallet, CreateWalletWithdrawalRequest request) {
        Map<UUID, BigDecimal> balances = walletService.findCenterBalances(wallet.getId());
        BigDecimal amount = request.getAmount();
        if (request.getCenterId() != null) {
            BigDecimal balance = balances.getOrDefault(request.getCenterId(), BigDecimal.ZERO);
            if (amount.compareTo(balance) > 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_CENTER_BALANCE);
            }
            return request.getCenterId();
        }
        if (balances.containsKey(null) && amount.compareTo(balances.get(null)) <= 0) {
            return null;
        }
        List<UUID> centers = balances.keySet().stream()
                .filter(centerId -> centerId != null)
                .toList();
        if (centers.size() > 1) {
            throw new AppException(ErrorCode.WITHDRAWAL_CENTER_REQUIRED);
        }
        if (centers.isEmpty() || amount.compareTo(balances.get(centers.get(0))) > 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_CENTER_BALANCE);
        }
        return centers.get(0);
    }

    // Phạm vi theo role; Optional rỗng = người gọi chưa có ví nên không có yêu cầu nào.
    private Optional<Specification<WalletWithdrawal>> buildScope(UUID actorId, Set<RoleCode> roles,
                                                                UUID claimCenterId, WalletWithdrawalScope scope) {
        boolean admin = roles.contains(RoleCode.ADMIN);
        if (scope == WalletWithdrawalScope.REVIEW) {
            if (admin) {
                return Optional.of((root, query, criteriaBuilder) -> criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("ownerType"), WalletOwnerType.CENTER),
                        criteriaBuilder.isNull(root.get("centerId"))));
            }
            if (!roles.contains(RoleCode.CENTER_MANAGER)) {
                throw new AppException(ErrorCode.WITHDRAWAL_NOT_REVIEWER);
            }
            if (claimCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            return Optional.of((root, query, criteriaBuilder) -> criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("centerId"), claimCenterId),
                    root.get("ownerType").in(CENTER_ROUTED_OWNERS)));
        }
        if (admin) {
            return Optional.of((root, query, criteriaBuilder) -> criteriaBuilder.conjunction());
        }
        return walletService.findOwnWallet(actorId, roles, claimCenterId)
                .map(wallet -> (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("walletId"),
                        wallet.getId()));
    }

    private Specification<WalletWithdrawal> hasStatusIn(Set<WalletWithdrawalStatus> statuses) {
        return (root, query, criteriaBuilder) -> root.get("status").in(statuses);
    }

    // Yêu cầu có mốc (gửi / hoàn tất / xử lý) rơi vào kỳ — tính số trong kỳ ở Java.
    private Specification<WalletWithdrawal> touchedBetween(Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.between(root.get(CREATED_AT), from, to),
                criteriaBuilder.between(root.get("completedAt"), from, to),
                criteriaBuilder.between(root.get("reviewedAt"), from, to));
    }

    private boolean isCenterRouted(WalletWithdrawal withdrawal) {
        return CENTER_ROUTED_OWNERS.contains(withdrawal.getOwnerType()) && withdrawal.getCenterId() != null;
    }

    private boolean isReviewer(Set<RoleCode> roles, UUID claimCenterId, WalletWithdrawal withdrawal) {
        if (isCenterRouted(withdrawal)) {
            return roles.contains(RoleCode.CENTER_MANAGER) && withdrawal.getCenterId().equals(claimCenterId);
        }
        return roles.contains(RoleCode.ADMIN);
    }

    private void checkReviewer(Set<RoleCode> roles, UUID claimCenterId, WalletWithdrawal withdrawal) {
        if (!isReviewer(roles, claimCenterId, withdrawal)) {
            throw new AppException(ErrorCode.WITHDRAWAL_NOT_REVIEWER);
        }
    }

    private boolean isManagerOnly(Set<RoleCode> roles) {
        return roles.contains(RoleCode.CENTER_MANAGER) && !roles.contains(RoleCode.ADMIN);
    }

    // Tiền khách / caregiver gắn trung tâm còn trong ví: khả dụng theo loại chủ + phần đang chờ rút.
    private void fillCustody(WalletWithdrawalStatisticsResponse statisticsResponse, UUID centerId) {
        BigDecimal customer = BigDecimal.ZERO;
        BigDecimal caregiver = BigDecimal.ZERO;
        BigDecimal held = BigDecimal.ZERO;
        for (WalletCenterTypeTotalView row : walletTransactionRepository.sumCustodyByCenter(centerId,
                CENTER_ROUTED_OWNERS)) {
            BigDecimal available = row.getTotalAmount().multiply(BigDecimal.valueOf(row.getType().availableSign()));
            held = held.add(row.getTotalAmount().multiply(BigDecimal.valueOf(row.getType().heldSign())));
            if (row.getOwnerType() == WalletOwnerType.CUSTOMER) {
                customer = customer.add(available);
            } else {
                caregiver = caregiver.add(available);
            }
        }
        customer = customer.max(BigDecimal.ZERO);
        caregiver = caregiver.max(BigDecimal.ZERO);
        held = held.max(BigDecimal.ZERO);
        statisticsResponse.setCustodyCustomerBalance(customer);
        statisticsResponse.setCustodyCaregiverBalance(caregiver);
        statisticsResponse.setCustodyHeldBalance(held);
        statisticsResponse.setCustodyTotal(customer.add(caregiver).add(held));
    }

    // Giả lập cổng chuyển khoản ngân hàng: luôn thành công, trả mã giao dịch.
    private String simulateBankTransfer(WalletWithdrawal withdrawal, Instant now) {
        String suffix = withdrawal.getId().toString().replace("-", "")
                .substring(0, SIMULATED_REFERENCE_SUFFIX_LENGTH)
                .toUpperCase(Locale.ROOT);
        String reference = SIMULATED_REFERENCE_PREFIX + now.atZone(ZONE).format(REFERENCE_TIME_FORMAT) + suffix;
        log.info("Simulated bank transfer {} of {} to {} account ending {}", reference, withdrawal.getAmount(),
                withdrawal.getBankName(), lastDigits(withdrawal.getBankAccountNumber()));
        return reference;
    }

    private String lastDigits(String accountNumber) {
        return accountNumber.length() <= 4 ? accountNumber : accountNumber.substring(accountNumber.length() - 4);
    }

    private boolean isWithin(Instant instant, Instant from, Instant to) {
        return instant != null && !instant.isBefore(from) && !instant.isAfter(to);
    }

    // Đủ mọi mốc trong kỳ (kể cả 0) để biểu đồ liền mạch; [0] = đã gửi, [1] = đã chuyển.
    private Map<String, BigDecimal[]> createBuckets(Instant from, Instant to, StatisticsGroupBy groupBy) {
        LocalDate start = from.atZone(ZONE).toLocalDate();
        LocalDate end = to.atZone(ZONE).toLocalDate();
        if (groupBy == StatisticsGroupBy.MONTH) {
            start = start.withDayOfMonth(1);
            end = end.withDayOfMonth(1);
        }
        ChronoUnit unit = groupBy == StatisticsGroupBy.MONTH ? ChronoUnit.MONTHS : ChronoUnit.DAYS;
        if (unit.between(start, end) >= MAX_SERIES_POINTS) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Map<String, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plus(1, unit)) {
            buckets.put(periodOf(cursor, groupBy), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        return buckets;
    }

    private void addToBucket(Map<String, BigDecimal[]> buckets, Instant instant, StatisticsGroupBy groupBy,
                             int index, BigDecimal amount) {
        BigDecimal[] bucket = buckets.get(periodOf(instant.atZone(ZONE).toLocalDate(), groupBy));
        if (bucket != null) {
            bucket[index] = bucket[index].add(amount);
        }
    }

    private String periodOf(LocalDate date, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? date.format(MONTH_FORMAT) : date.format(DAY_FORMAT);
    }

    // Cộng dồn trong một lần tính thống kê.
    private static final class OwnerTotals {
        long pendingCount;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        long completedCount;
        BigDecimal completedAmount = BigDecimal.ZERO;
    }

    private static final class PeriodTotals {
        long requestedCount;
        BigDecimal requestedAmount = BigDecimal.ZERO;
        long completedCount;
        BigDecimal completedAmount = BigDecimal.ZERO;
        long rejectedCount;
        BigDecimal rejectedAmount = BigDecimal.ZERO;
        long processingMinutes;
    }
}
