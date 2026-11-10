package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.DepositForfeitureResponse;
import mss301.paymentservice.dto.response.DepositForfeitureSummaryResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RoleCode;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import mss301.paymentservice.mapper.DepositForfeitureMapper;
import mss301.paymentservice.repository.DepositForfeitureRepository;
import mss301.paymentservice.service.DepositForfeitureService;
import mss301.paymentservice.service.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class DepositForfeitureServiceImpl implements DepositForfeitureService {

    static int MAX_PAGE_SIZE = 100;

    DepositForfeitureRepository depositForfeitureRepository;
    DepositForfeitureMapper depositForfeitureMapper;
    WalletService walletService;

    // MANDATORY: commit / rollback cùng lệnh hoàn tiền tạo ra phần bị giữ. Buổi đã có caregiver → caregiver hưởng toàn bộ.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(OccurrenceFinancial occurrenceFinancial, UUID paymentId, BigDecimal amount, RefundReason reason,
                       String rule, String idempotencyKey) {
        if (amount == null || amount.signum() <= 0
                || depositForfeitureRepository.existsByIdempotencyKeyAndOccurrenceId(idempotencyKey,
                occurrenceFinancial.getOccurrenceId())) {
            return;
        }
        ForfeitureBeneficiary beneficiary = occurrenceFinancial.getCaregiverUserId() == null
                ? ForfeitureBeneficiary.CENTER
                : ForfeitureBeneficiary.CAREGIVER;
        DepositForfeiture forfeiture = depositForfeitureRepository.save(depositForfeitureMapper.toForfeiture(
                occurrenceFinancial, paymentId, amount, beneficiary, reason, rule, idempotencyKey));
        // Ví (I1): phần bị giữ vào ví caregiver được giao, buổi chưa phân công thì vào ví trung tâm — cùng transaction.
        walletService.creditForfeiture(forfeiture);
        log.info("Occurrence {} of booking {}: {} kept from the customer goes to {}", occurrenceFinancial.getOccurrenceId(),
                occurrenceFinancial.getBookingId(), amount, beneficiary);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<DepositForfeitureResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                     UUID claimCenterId, UUID centerId, Instant from,
                                                                     Instant to, int page, int size) {
        checkRange(from, to);
        Scope scope = resolveScope(actorId, roles, claimCenterId, centerId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<DepositForfeiture> forfeitures = depositForfeitureRepository.findPage(scope.centerId(),
                scope.caregiverUserId(), scope.beneficiary(), from, to, pageRequest);
        PageResponse<DepositForfeitureResponse> pageResponse = PageResponse.of(forfeitures,
                depositForfeitureMapper.toResponseList(forfeitures.getContent()));
        return APIResponse.success("Lấy danh sách tiền cọc bị giữ thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<DepositForfeitureSummaryResponse> getSummary(UUID actorId, Set<RoleCode> roles,
                                                                    UUID claimCenterId, UUID centerId, Instant from,
                                                                    Instant to) {
        checkRange(from, to);
        Scope scope = resolveScope(actorId, roles, claimCenterId, centerId);
        BigDecimal caregiverAmount = depositForfeitureRepository.sumByBeneficiary(scope.centerId(),
                scope.caregiverUserId(), ForfeitureBeneficiary.CAREGIVER, from, to);
        BigDecimal centerAmount = scope.caregiverUserId() == null
                ? depositForfeitureRepository.sumByBeneficiary(scope.centerId(), null, ForfeitureBeneficiary.CENTER,
                from, to)
                : BigDecimal.ZERO;
        DepositForfeitureSummaryResponse summaryResponse = depositForfeitureMapper.toSummaryResponse(centerAmount,
                caregiverAmount, centerAmount.add(caregiverAmount));
        return APIResponse.success("Thống kê tiền cọc bị giữ thành công", summaryResponse);
    }

    // CAREGIVER: chỉ phần mình hưởng; CENTER_MANAGER: trung tâm trong token (bỏ qua centerId truyền lên); ADMIN: tùy chọn.
    private Scope resolveScope(UUID actorId, Set<RoleCode> roles, UUID claimCenterId, UUID centerId) {
        if (roles.contains(RoleCode.ADMIN)) {
            return new Scope(centerId, null, null);
        }
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            if (claimCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            return new Scope(claimCenterId, null, null);
        }
        return new Scope(null, actorId, ForfeitureBeneficiary.CAREGIVER);
    }

    private void checkRange(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
    }

    private record Scope(UUID centerId, UUID caregiverUserId, ForfeitureBeneficiary beneficiary) {
    }
}
