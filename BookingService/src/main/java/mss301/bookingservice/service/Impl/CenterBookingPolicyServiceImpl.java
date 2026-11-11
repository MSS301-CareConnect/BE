package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.CenterBookingPolicyRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CenterBookingPolicyResponse;
import mss301.bookingservice.entity.CenterBookingPolicy;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.CenterBookingPolicyMapper;
import mss301.bookingservice.repository.CenterBookingPolicyRepository;
import mss301.bookingservice.service.CenterBookingPolicyService;
import mss301.bookingservice.service.CenterPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CenterBookingPolicyServiceImpl implements CenterBookingPolicyService {

    CenterBookingPolicyRepository centerBookingPolicyRepository;
    CenterBookingPolicyMapper centerBookingPolicyMapper;
    CenterPort centerPort;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;

    // Khách xem policy của trung tâm bất kỳ (màn đặt lịch); manager chỉ trung tâm trong token; admin mọi trung tâm.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<CenterBookingPolicyResponse> get(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId) {
        if (!roles.contains(RoleCode.ADMIN) && !roles.contains(RoleCode.CUSTOMER)) {
            checkManagerScope(scopeCenterId, centerId);
        }
        Optional<CenterBookingPolicy> savedPolicy = centerBookingPolicyRepository.findByCenterIdAndDeletedAtIsNull(centerId);
        CenterBookingPolicy policy = savedPolicy.orElseGet(() -> buildDefaultPolicy(centerId));
        CenterBookingPolicyResponse policyResponse = centerBookingPolicyMapper.toResponse(policy, savedPolicy.isPresent());
        return APIResponse.success("Lấy chính sách booking của trung tâm thành công", policyResponse);
    }

    // Cập nhật chính sách booking của trung tâm (admin chọn trung tâm, manager trung tâm mình).
    @Override
    public APIResponse<CenterBookingPolicyResponse> update(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
                                                           UUID centerId, CenterBookingPolicyRequest request) {
        if (roles.contains(RoleCode.ADMIN)) {
            centerPort.checkExists(centerId);
        } else {
            checkManagerScope(scopeCenterId, centerId);
        }
        checkConsistency(request);
        CenterBookingPolicy savedPolicy;
        try {
            savedPolicy = transactionTemplate.execute(status -> savePolicy(actorId, centerId, request));
        } catch (DataIntegrityViolationException e) {
            // Hai lần tạo policy đầu tiên song song: uk_center_booking_policies_center chặn lần thứ hai.
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
        log.info("Booking policy of center {} updated by {}", centerId, actorId);
        CenterBookingPolicyResponse policyResponse = centerBookingPolicyMapper.toResponse(savedPolicy, true);
        return APIResponse.success("Cập nhật chính sách booking của trung tâm thành công", policyResponse);
    }

    // Chính sách booking của trung tâm, chưa có thì dùng mặc định.
    @Override
    @Transactional(readOnly = true)
    public CenterBookingPolicy findEffective(UUID centerId) {
        return centerBookingPolicyRepository.findByCenterIdAndDeletedAtIsNull(centerId)
                .orElseGet(() -> buildDefaultPolicy(centerId));
    }

    // Tạo mới hoặc cập nhật chính sách booking của trung tâm.
    private CenterBookingPolicy savePolicy(UUID actorId, UUID centerId, CenterBookingPolicyRequest request) {
        Optional<CenterBookingPolicy> existing = centerBookingPolicyRepository.findByCenterIdAndDeletedAtIsNull(centerId);
        if (existing.isEmpty()) {
            if (request.getVersion() != null) {
                throw new AppException(ErrorCode.CONCURRENT_UPDATE);
            }
            return centerBookingPolicyRepository.saveAndFlush(
                    centerBookingPolicyMapper.toCenterBookingPolicy(request, centerId, actorId));
        }
        CenterBookingPolicy policy = existing.get();
        // Sửa trên bản đã xem: version khác nghĩa là người khác vừa đổi policy.
        if (!Objects.equals(policy.getVersion(), request.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }
        centerBookingPolicyMapper.update(request, actorId, policy);
        return centerBookingPolicyRepository.saveAndFlush(policy);
    }

    // Ràng buộc giữa các giá trị (§8 "policy cần hợp lý với nhau"): RECURRING / CUSTOM phải đặt trước lâu hơn hạn trả
    // phần còn lại, nếu không thì không booking nào còn deadline hợp lệ; hoàn sau mốc không hơn trước mốc.
    private void checkConsistency(CenterBookingPolicyRequest request) {
        int remainingDueBefore = request.getRemainingPaymentDueBeforeMinutes();
        if (request.getRecurringMinAdvanceMinutes() <= remainingDueBefore
                || request.getCustomMinAdvanceMinutes() <= remainingDueBefore) {
            throw new AppException(ErrorCode.INVALID_BOOKING_POLICY);
        }
        if (request.getRefundPercentAfterThreshold().compareTo(request.getRefundPercentBeforeThreshold()) > 0) {
            throw new AppException(ErrorCode.INVALID_BOOKING_POLICY);
        }
        // Mốc "không hoàn" nằm trong khoảng sau mốc hoàn: dài hơn mốc hoàn thì phần "% sau mốc" không bao giờ áp dụng.
        if (request.getLateCancelNoRefundMinutes() > request.getRefundThresholdMinutes()) {
            throw new AppException(ErrorCode.INVALID_BOOKING_POLICY);
        }
        if (new HashSet<>(request.getRemainingPaymentReminderMinutes()).size()
                != request.getRemainingPaymentReminderMinutes().size()) {
            throw new AppException(ErrorCode.INVALID_BOOKING_POLICY);
        }
    }

    // Manager chỉ đọc / sửa policy trung tâm của mình; trung tâm khác → 404 như không tồn tại.
    private void checkManagerScope(UUID scopeCenterId, UUID centerId) {
        if (scopeCenterId == null) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
        if (!scopeCenterId.equals(centerId)) {
            throw new AppException(ErrorCode.CENTER_NOT_FOUND);
        }
    }

    // Dựng chính sách booking mặc định từ cấu hình.
    private CenterBookingPolicy buildDefaultPolicy(UUID centerId) {
        return centerBookingPolicyMapper.toDefaultPolicy(bookingProperties.getPolicyDefaults(), centerId);
    }
}
