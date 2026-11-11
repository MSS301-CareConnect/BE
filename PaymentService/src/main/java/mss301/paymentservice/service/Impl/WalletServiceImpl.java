package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.request.WalletPosting;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.dto.response.WalletBalanceTotalResponse;
import mss301.paymentservice.dto.response.WalletCenterBalanceResponse;
import mss301.paymentservice.dto.response.WalletResponse;
import mss301.paymentservice.dto.response.WalletSeriesPointResponse;
import mss301.paymentservice.dto.response.WalletStatisticsResponse;
import mss301.paymentservice.dto.response.WalletTransactionResponse;
import mss301.paymentservice.dto.response.WalletTypeTotalResponse;
import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.entity.Payment;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.PaymentStatus;
import mss301.paymentservice.enums.RefundChannel;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.enums.StatisticsGroupBy;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.WalletMapper;
import mss301.paymentservice.repository.PaymentRepository;
import mss301.paymentservice.repository.RefundRepository;
import mss301.paymentservice.repository.WalletCenterTypeTotalView;
import mss301.paymentservice.repository.WalletRepository;
import mss301.paymentservice.repository.WalletTransactionRepository;
import mss301.paymentservice.repository.WalletWithdrawalRepository;
import mss301.paymentservice.service.WalletService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WalletServiceImpl implements WalletService {
    // Ví nền tảng có đúng một dòng: owner_id cố định là UUID 0.
    static UUID PLATFORM_OWNER_ID = new UUID(0L, 0L);
    // 2026-11-11: trung tâm / nền tảng nhận tiền thẳng vào tài khoản của mình — sổ của họ là doanh thu, không rút.
    static Set<WalletOwnerType> WITHDRAWABLE_OWNERS = EnumSet.of(WalletOwnerType.CUSTOMER, WalletOwnerType.CAREGIVER);
    static ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    static int MAX_PAGE_SIZE = 100;
    static int MAX_SERIES_POINTS = 400;
    static String REFUND_KEY = "REFUND:";
    static String FORFEIT_KEY = "FORFEIT:";
    static String SETTLE_KEY = "SETTLE:";
    static String COMMISSION_KEY = "COMMISSION:";
    static String COMPLAINT_KEY = "COMPLAINT:";
    static Set<WalletWithdrawalStatus> OPEN_WITHDRAWAL_STATUSES = EnumSet.of(WalletWithdrawalStatus.PENDING,
            WalletWithdrawalStatus.APPROVED);
    // Biến động tính là thu nhập của từng loại chủ ví (dashboard + biểu đồ).
    static Map<WalletOwnerType, Set<WalletTransactionType>> INCOME_TYPES = Map.of(
            WalletOwnerType.CUSTOMER, EnumSet.of(WalletTransactionType.REFUND_CREDIT),
            WalletOwnerType.CAREGIVER, EnumSet.of(WalletTransactionType.FORFEIT_CREDIT),
            WalletOwnerType.CENTER, EnumSet.of(WalletTransactionType.SERVICE_SETTLEMENT,
                    WalletTransactionType.FORFEIT_CREDIT),
            WalletOwnerType.PLATFORM, EnumSet.of(WalletTransactionType.COMMISSION_CREDIT));

    WalletRepository walletRepository;
    WalletTransactionRepository walletTransactionRepository;
    WalletWithdrawalRepository walletWithdrawalRepository;
    PaymentRepository paymentRepository;
    RefundRepository refundRepository;
    WalletMapper walletMapper;
    PlatformTransactionManager transactionManager;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletResponse> getMine(UUID actorId, Set<RoleCode> roles, UUID claimCenterId) {
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        WalletResponse walletResponse = walletRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id())
                .map(wallet -> walletMapper.toResponse(wallet, isWithdrawable(wallet)))
                .orElseGet(() -> walletMapper.toEmptyResponse(owner.type(), owner.id(), false));
        return APIResponse.success("Lấy thông tin ví thành công", walletResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletResponse> get(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
        WalletResponse walletResponse = walletMapper.toResponse(wallet, isWithdrawable(wallet));
        return APIResponse.success("Lấy thông tin ví thành công", walletResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WalletTransactionResponse>> listMyTransactions(UUID actorId, Set<RoleCode> roles,
                                                                                   UUID claimCenterId,
                                                                                   WalletTransactionType type,
                                                                                   Instant from, Instant to, int page,
                                                                                   int size) {
        checkRange(from, to);
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id()).orElse(null);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        if (wallet == null) {
            PageResponse<WalletTransactionResponse> emptyPage = PageResponse.of(Page.empty(pageRequest), List.of());
            return APIResponse.success("Lấy lịch sử giao dịch ví thành công", emptyPage);
        }
        return buildTransactionPage(wallet.getId(), type, from, to, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WalletTransactionResponse>> listTransactions(UUID walletId,
                                                                                 WalletTransactionType type,
                                                                                 Instant from, Instant to, int page,
                                                                                 int size) {
        checkRange(from, to);
        if (!walletRepository.existsById(walletId)) {
            throw new AppException(ErrorCode.WALLET_NOT_FOUND);
        }
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return buildTransactionPage(walletId, type, from, to, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<WalletCenterBalanceResponse>> listMyCenterBalances(UUID actorId, Set<RoleCode> roles,
                                                                              UUID claimCenterId) {
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        if (owner.type() != WalletOwnerType.CUSTOMER && owner.type() != WalletOwnerType.CAREGIVER) {
            throw new AppException(ErrorCode.WALLET_NOT_WITHDRAWABLE);
        }
        List<WalletCenterBalanceResponse> balances = walletRepository.findByOwnerTypeAndOwnerId(owner.type(),
                        owner.id())
                .map(wallet -> findCenterBalances(wallet.getId()).entrySet().stream()
                        .map(entry -> walletMapper.toCenterBalanceResponse(entry.getKey(), entry.getValue()))
                        .toList())
                .orElse(List.of());
        return APIResponse.success("Lấy số dư theo trung tâm thành công", balances);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WalletStatisticsResponse> getMyStatistics(UUID actorId, Set<RoleCode> roles,
                                                                 UUID claimCenterId, Instant from, Instant to,
                                                                 StatisticsGroupBy groupBy) {
        checkRange(from, to);
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        StatisticsGroupBy grouping = groupBy == null ? StatisticsGroupBy.DAY : groupBy;
        Map<String, SeriesBucket> buckets = createBuckets(from, to, grouping);
        Wallet wallet = walletRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id()).orElse(null);
        Set<WalletTransactionType> incomeTypes = INCOME_TYPES.get(owner.type());
        List<WalletTypeTotalResponse> byType = wallet == null
                ? List.of()
                : walletTransactionRepository.sumByType(wallet.getId(), from, to);
        Map<WalletTransactionType, BigDecimal> totals = new EnumMap<>(WalletTransactionType.class);
        for (WalletTypeTotalResponse total : byType) {
            totals.put(total.getType(), total.getTotalAmount());
        }
        BigDecimal totalIncome = BigDecimal.ZERO;
        for (WalletTransactionType incomeType : incomeTypes) {
            totalIncome = totalIncome.add(totals.getOrDefault(incomeType, BigDecimal.ZERO));
        }
        BigDecimal complaintDebits = totals.getOrDefault(WalletTransactionType.COMPLAINT_DEBIT, BigDecimal.ZERO);
        totalIncome = totalIncome.subtract(complaintDebits);
        long bookingCount = 0;
        if (wallet != null) {
            bookingCount = walletTransactionRepository.countBookings(wallet.getId(), incomeTypes, from, to);
            fillWalletSeries(buckets, wallet.getId(), incomeTypes, from, to, grouping);
        }
        if (owner.type() == WalletOwnerType.CUSTOMER) {
            fillSpentSeries(buckets, owner.id(), from, to, grouping);
        }
        List<WalletSeriesPointResponse> series = new ArrayList<>();
        for (Map.Entry<String, SeriesBucket> entry : buckets.entrySet()) {
            SeriesBucket bucket = entry.getValue();
            series.add(walletMapper.toSeriesPoint(entry.getKey(), bucket.income, bucket.withdrawn,
                    owner.type() == WalletOwnerType.CUSTOMER ? bucket.spent : null));
        }
        WalletStatisticsResponse statisticsResponse = walletMapper.toStatisticsResponse(owner.type(), from, to, grouping,
                wallet == null ? BigDecimal.ZERO : wallet.getAvailableBalance(),
                wallet == null ? BigDecimal.ZERO : wallet.getHeldBalance(), totalIncome,
                totals.getOrDefault(WalletTransactionType.WITHDRAWAL_PAID, BigDecimal.ZERO), bookingCount, byType, series);
        switch (owner.type()) {
            case CUSTOMER -> fillCustomerStatistics(statisticsResponse, owner.id(), from, to);
            case CAREGIVER -> statisticsResponse.setForfeitIncome(
                    totals.getOrDefault(WalletTransactionType.FORFEIT_CREDIT, BigDecimal.ZERO));
            case CENTER -> fillCenterStatistics(statisticsResponse, totals, owner.id(), from, to);
            case PLATFORM -> fillPlatformStatistics(statisticsResponse, totals, from, to);
        }
        return APIResponse.success("Thống kê ví thành công", statisticsResponse);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditRefund(Refund refund) {
        Wallet wallet = lockOrCreateWallet(WalletOwnerType.CUSTOMER, refund.getCustomerId());
        post(wallet, new WalletPosting(WalletTransactionType.REFUND_CREDIT, refund.getAmount(),
                REFUND_KEY + refund.getId(), refund.getCenterId(), refund.getBookingId(), refund.getOccurrenceId(),
                refund.getId(), null, "reason=" + refund.getReason()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void creditForfeiture(DepositForfeiture forfeiture) {
        boolean caregiver = forfeiture.getBeneficiary() == ForfeitureBeneficiary.CAREGIVER;
        Wallet wallet = caregiver
                ? lockOrCreateWallet(WalletOwnerType.CAREGIVER, forfeiture.getCaregiverUserId())
                : lockOrCreateWallet(WalletOwnerType.CENTER, forfeiture.getCenterId());
        // Khóa theo buổi (không theo idempotency key): một buổi chỉ bị giữ cọc một lần trong mọi lệnh hoàn.
        post(wallet, new WalletPosting(WalletTransactionType.FORFEIT_CREDIT, forfeiture.getAmount(),
                FORFEIT_KEY + forfeiture.getOccurrenceId(), forfeiture.getCenterId(), forfeiture.getBookingId(),
                forfeiture.getOccurrenceId(), null, null, "reason=" + forfeiture.getReason()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleOccurrence(OccurrenceFinancial occurrenceFinancial, BigDecimal centerAmount,
                                 BigDecimal commission) {
        UUID occurrenceId = occurrenceFinancial.getOccurrenceId();
        // Thứ tự khóa cố định: ví trung tâm rồi ví nền tảng.
        Wallet centerWallet = lockOrCreateWallet(WalletOwnerType.CENTER, occurrenceFinancial.getCenterId());
        post(centerWallet, new WalletPosting(WalletTransactionType.SERVICE_SETTLEMENT, centerAmount,
                SETTLE_KEY + occurrenceId, occurrenceFinancial.getCenterId(), occurrenceFinancial.getBookingId(),
                occurrenceId, null, null, "commission=" + commission));
        Wallet platformWallet = lockOrCreateWallet(WalletOwnerType.PLATFORM, PLATFORM_OWNER_ID);
        post(platformWallet, new WalletPosting(WalletTransactionType.COMMISSION_CREDIT, commission,
                COMMISSION_KEY + occurrenceId, occurrenceFinancial.getCenterId(), occurrenceFinancial.getBookingId(),
                occurrenceId, null, null, null));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal debitComplaintRefund(UUID centerId, UUID bookingId, UUID complaintId, BigDecimal amount) {
        Wallet wallet = walletRepository.findForUpdate(WalletOwnerType.CENTER, centerId).orElse(null);
        if (wallet == null || amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal settled = walletTransactionRepository.sumByBooking(wallet.getId(), bookingId,
                WalletTransactionType.SERVICE_SETTLEMENT);
        BigDecimal debited = walletTransactionRepository.sumByBooking(wallet.getId(), bookingId,
                WalletTransactionType.COMPLAINT_DEBIT);
        // Chỉ trừ phần trung tâm đã thực nhận của booking; booking chưa cộng gì thì tiền hoàn chưa từng vào ví trung tâm.
        BigDecimal debit = amount.min(settled.subtract(debited));
        if (debit.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        boolean posted = post(wallet, new WalletPosting(WalletTransactionType.COMPLAINT_DEBIT, debit,
                COMPLAINT_KEY + complaintId, centerId, bookingId, null, null, null, "complaint=" + complaintId));
        log.info("Center {} wallet debited {} for complaint {} on booking {}", centerId, debit, complaintId, bookingId);
        return posted ? debit : BigDecimal.ZERO;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void debitComplaintCommission(UUID centerId, UUID bookingId, UUID complaintId, BigDecimal amount) {
        Wallet wallet = walletRepository.findForUpdate(WalletOwnerType.PLATFORM, PLATFORM_OWNER_ID).orElse(null);
        if (wallet == null || amount == null || amount.signum() <= 0) {
            return;
        }
        BigDecimal collected = walletTransactionRepository.sumByBooking(wallet.getId(), bookingId,
                WalletTransactionType.COMMISSION_CREDIT);
        BigDecimal returned = walletTransactionRepository.sumByBooking(wallet.getId(), bookingId,
                WalletTransactionType.COMPLAINT_DEBIT);
        // Nền tảng chỉ trả lại hoa hồng đã thu của chính booking này, không gánh thay trung tâm.
        BigDecimal debit = amount.min(collected.subtract(returned)).min(wallet.getAvailableBalance());
        if (debit.signum() <= 0) {
            return;
        }
        post(wallet, new WalletPosting(WalletTransactionType.COMPLAINT_DEBIT, debit, COMPLAINT_KEY + complaintId,
                centerId, bookingId, null, null, null, "complaint=" + complaintId + " commission"));
        log.info("Platform wallet returned {} of commission for complaint {} on booking {}", debit, complaintId,
                bookingId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lockOwnWallet(UUID actorId, Set<RoleCode> roles, UUID claimCenterId) {
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        if (!WITHDRAWABLE_OWNERS.contains(owner.type())) {
            throw new AppException(ErrorCode.WALLET_NOT_WITHDRAWABLE);
        }
        return lockOrCreateWallet(owner.type(), owner.id());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findOwnWallet(UUID actorId, Set<RoleCode> roles, UUID claimCenterId) {
        WalletOwner owner = resolveOwner(actorId, roles, claimCenterId);
        return walletRepository.findByOwnerTypeAndOwnerId(owner.type(), owner.id());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lockWallet(UUID walletId) {
        return walletRepository.findForUpdateById(walletId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
    }

    // Cộng dồn theo center_id. Yêu cầu rút trước 2026-11-10 ghi WITHDRAWAL_HOLD / PAID không gắn trung tâm nên nhóm "không
    // trung tâm" có thể âm — phần âm trừ vào các trung tâm lớn nhất để tổng vẫn khớp số dư khả dụng.
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> findCenterBalances(UUID walletId) {
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (WalletCenterTypeTotalView row : walletTransactionRepository.sumByCenterForWallet(walletId)) {
            BigDecimal signed = row.getTotalAmount().multiply(BigDecimal.valueOf(row.getType().availableSign()));
            balances.merge(row.getCenterId(), signed, BigDecimal::add);
        }
        BigDecimal deficit = balances.getOrDefault(null, BigDecimal.ZERO).min(BigDecimal.ZERO).negate();
        List<UUID> centers = balances.entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (UUID centerId : centers) {
            if (deficit.signum() <= 0) {
                break;
            }
            BigDecimal taken = balances.get(centerId).max(BigDecimal.ZERO).min(deficit);
            balances.put(centerId, balances.get(centerId).subtract(taken));
            deficit = deficit.subtract(taken);
        }
        Map<UUID, BigDecimal> positive = new LinkedHashMap<>();
        balances.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .forEach(entry -> positive.put(entry.getKey(), entry.getValue()));
        return positive;
    }

    // Ví đã khóa ở caller. Kiểm trùng referenceKey sau khi khóa nên hai lần xử lý cùng sự kiện không thể cùng lọt.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean post(Wallet wallet, WalletPosting posting) {
        if (posting.amount() == null || posting.amount().signum() <= 0) {
            return false;
        }
        if (walletTransactionRepository.existsByWalletIdAndReferenceKey(wallet.getId(), posting.referenceKey())) {
            log.warn("Wallet {} already has {} — skipped", wallet.getId(), posting.referenceKey());
            return false;
        }
        WalletTransactionType type = posting.type();
        BigDecimal availableBefore = wallet.getAvailableBalance();
        BigDecimal heldBefore = wallet.getHeldBalance();
        BigDecimal availableAfter = availableBefore.add(posting.amount().multiply(BigDecimal.valueOf(type.availableSign())));
        BigDecimal heldAfter = heldBefore.add(posting.amount().multiply(BigDecimal.valueOf(type.heldSign())));
        // Chỉ ví trung tâm được âm, và chỉ do hoàn khiếu nại (trung tâm nợ nền tảng); mọi khoản khác không vượt số dư.
        boolean negativeAllowed = wallet.getOwnerType() == WalletOwnerType.CENTER
                && type == WalletTransactionType.COMPLAINT_DEBIT;
        boolean availableDropsBelowZero = type.availableSign() < 0 && availableAfter.signum() < 0;
        if ((availableDropsBelowZero && !negativeAllowed) || heldAfter.signum() < 0) {
            throw new AppException(ErrorCode.INSUFFICIENT_WALLET_BALANCE);
        }
        wallet.setAvailableBalance(availableAfter);
        wallet.setHeldBalance(heldAfter);
        walletTransactionRepository.save(walletMapper.toTransaction(posting, wallet.getId(), availableBefore,
                availableAfter, heldBefore, heldAfter, clock.instant()));
        log.info("Wallet {} ({}) {} {}: available {} -> {}", wallet.getId(), wallet.getOwnerType(), type,
                posting.amount(), availableBefore, availableAfter);
        return true;
    }

    // Ví tạo lười ở biến động đầu tiên, trong transaction riêng: hai sự kiện song song cho cùng chủ mới → một bên chèn,
    // bên kia gặp unique uk_wallets_owner rồi đọc lại.
    private Wallet lockOrCreateWallet(WalletOwnerType ownerType, UUID ownerId) {
        Optional<Wallet> existing = walletRepository.findForUpdate(ownerType, ownerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            requiresNew.executeWithoutResult(status -> walletRepository.saveAndFlush(
                    walletMapper.toWallet(ownerType, ownerId)));
        } catch (DataIntegrityViolationException e) {
            log.info("Wallet {} {} was created concurrently", ownerType, ownerId);
        }
        return walletRepository.findForUpdate(ownerType, ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));
    }

    // Chủ ví theo role trong token: ADMIN → ví nền tảng, CENTER_MANAGER → ví trung tâm trong token, còn lại → ví cá nhân.
    private WalletOwner resolveOwner(UUID actorId, Set<RoleCode> roles, UUID claimCenterId) {
        if (roles.contains(RoleCode.ADMIN)) {
            return new WalletOwner(WalletOwnerType.PLATFORM, PLATFORM_OWNER_ID);
        }
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            if (claimCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            return new WalletOwner(WalletOwnerType.CENTER, claimCenterId);
        }
        if (roles.contains(RoleCode.CAREGIVER)) {
            return new WalletOwner(WalletOwnerType.CAREGIVER, actorId);
        }
        if (roles.contains(RoleCode.CUSTOMER)) {
            return new WalletOwner(WalletOwnerType.CUSTOMER, actorId);
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    private boolean isWithdrawable(Wallet wallet) {
        return WITHDRAWABLE_OWNERS.contains(wallet.getOwnerType()) && wallet.getAvailableBalance().signum() > 0;
    }

    private APIResponse<PageResponse<WalletTransactionResponse>> buildTransactionPage(UUID walletId,
                                                                                      WalletTransactionType type,
                                                                                      Instant from, Instant to,
                                                                                      PageRequest pageRequest) {
        Page<WalletTransaction> transactions = walletTransactionRepository.findPage(walletId, type, from, to,
                pageRequest);
        PageResponse<WalletTransactionResponse> pageResponse = PageResponse.of(transactions,
                walletMapper.toTransactionResponseList(transactions.getContent()));
        return APIResponse.success("Lấy lịch sử giao dịch ví thành công", pageResponse);
    }

    private void fillCustomerStatistics(WalletStatisticsResponse statisticsResponse, UUID customerId, Instant from,
                                        Instant to) {
        BigDecimal totalPaid = paymentRepository.sumPaidByCustomer(customerId, PaymentStatus.SUCCESS, from, to);
        BigDecimal toWallet = refundRepository.sumRefundedByCustomer(customerId, RefundStatus.REFUNDED,
                RefundChannel.WALLET, from, to);
        BigDecimal toGateway = refundRepository.sumRefundedByCustomer(customerId, RefundStatus.REFUNDED,
                RefundChannel.GATEWAY, from, to);
        statisticsResponse.setTotalPaid(totalPaid);
        statisticsResponse.setRefundedToWallet(toWallet);
        statisticsResponse.setRefundedToGateway(toGateway);
        statisticsResponse.setNetSpent(totalPaid.subtract(toWallet).subtract(toGateway));
    }

    private void fillCenterStatistics(WalletStatisticsResponse statisticsResponse,
                                      Map<WalletTransactionType, BigDecimal> totals, UUID centerId, Instant from,
                                      Instant to) {
        statisticsResponse.setServiceIncome(totals.getOrDefault(WalletTransactionType.SERVICE_SETTLEMENT,
                BigDecimal.ZERO));
        statisticsResponse.setForfeitIncome(totals.getOrDefault(WalletTransactionType.FORFEIT_CREDIT, BigDecimal.ZERO));
        statisticsResponse.setComplaintDebits(totals.getOrDefault(WalletTransactionType.COMPLAINT_DEBIT,
                BigDecimal.ZERO));
        statisticsResponse.setCommissionDeducted(walletTransactionRepository.sumByCenter(centerId,
                WalletTransactionType.COMMISSION_CREDIT, from, to));
    }

    private void fillPlatformStatistics(WalletStatisticsResponse statisticsResponse,
                                        Map<WalletTransactionType, BigDecimal> totals, Instant from, Instant to) {
        statisticsResponse.setCommissionIncome(totals.getOrDefault(WalletTransactionType.COMMISSION_CREDIT,
                BigDecimal.ZERO));
        statisticsResponse.setGrossCollected(paymentRepository.sumPaid(PaymentStatus.SUCCESS, from, to, null));
        statisticsResponse.setTotalRefunded(paymentRepository.sumRefunded(PaymentStatus.SUCCESS, from, to, null));
        statisticsResponse.setRefundedToWallet(walletTransactionRepository.sumByOwnerType(WalletOwnerType.CUSTOMER,
                WalletTransactionType.REFUND_CREDIT, from, to));
        // Tiền nền tảng đang giữ hộ khách / caregiver / trung tâm (ví nền tảng là tiền của chính nền tảng).
        List<WalletBalanceTotalResponse> liabilities = walletRepository.sumBalancesByOwnerType().stream()
                .filter(total -> total.getOwnerType() != WalletOwnerType.PLATFORM)
                .toList();
        statisticsResponse.setLiabilities(liabilities);
        statisticsResponse.setPendingWithdrawalCount(walletWithdrawalRepository.countByStatusIn(
                OPEN_WITHDRAWAL_STATUSES));
        statisticsResponse.setPendingWithdrawalAmount(walletWithdrawalRepository.sumAmountByStatusIn(
                OPEN_WITHDRAWAL_STATUSES));
    }

    // Đủ mọi mốc trong kỳ (kể cả mốc 0) để biểu đồ liền mạch; giới hạn số mốc tránh kỳ quá dài theo ngày.
    private Map<String, SeriesBucket> createBuckets(Instant from, Instant to, StatisticsGroupBy groupBy) {
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
        Map<String, SeriesBucket> buckets = new LinkedHashMap<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); cursor = cursor.plus(1, unit)) {
            buckets.put(periodOf(cursor, groupBy), new SeriesBucket());
        }
        return buckets;
    }

    private void fillWalletSeries(Map<String, SeriesBucket> buckets, UUID walletId,
                                  Set<WalletTransactionType> incomeTypes, Instant from, Instant to,
                                  StatisticsGroupBy groupBy) {
        Set<WalletTransactionType> types = EnumSet.copyOf(incomeTypes);
        types.add(WalletTransactionType.WITHDRAWAL_PAID);
        types.add(WalletTransactionType.COMPLAINT_DEBIT);
        for (WalletTransaction transaction : walletTransactionRepository.findAllForSeries(walletId, types, from, to)) {
            SeriesBucket bucket = buckets.get(periodOf(transaction.getCreatedAt().atZone(ZONE).toLocalDate(), groupBy));
            if (bucket == null) {
                continue;
            }
            if (transaction.getType() == WalletTransactionType.WITHDRAWAL_PAID) {
                bucket.withdrawn = bucket.withdrawn.add(transaction.getAmount());
            } else if (transaction.getType() == WalletTransactionType.COMPLAINT_DEBIT) {
                bucket.income = bucket.income.subtract(transaction.getAmount());
            } else {
                bucket.income = bucket.income.add(transaction.getAmount());
            }
        }
    }

    private void fillSpentSeries(Map<String, SeriesBucket> buckets, UUID customerId, Instant from, Instant to,
                                 StatisticsGroupBy groupBy) {
        for (Payment payment : paymentRepository.findAllPaidByCustomer(customerId, PaymentStatus.SUCCESS, from, to)) {
            SeriesBucket bucket = buckets.get(periodOf(payment.getPaidAt().atZone(ZONE).toLocalDate(), groupBy));
            if (bucket != null) {
                bucket.spent = bucket.spent.add(payment.getAmount());
            }
        }
    }

    private String periodOf(LocalDate date, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? date.format(MONTH_FORMAT) : date.format(DAY_FORMAT);
    }

    private void checkRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private record WalletOwner(WalletOwnerType type, UUID id) {
    }

    // Cộng dồn một mốc biểu đồ (chỉ sống trong một lần tính).
    private static final class SeriesBucket {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal withdrawn = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
    }
}
