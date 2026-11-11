package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.CaregiverWithdrawalRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverWithdrawalResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CaregiverWithdrawal;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CaregiverWithdrawalReason;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.CaregiverWithdrawalMapper;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.CaregiverWithdrawalRepository;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.CaregiverAssignmentService;
import mss301.bookingservice.service.CaregiverWithdrawalService;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverWithdrawalServiceImpl implements CaregiverWithdrawalService {

    // Caregiver chỉ thấy / làm buổi của booking đã xác nhận (BookingStatus.CAREGIVER_VISIBLE trừ booking đã đóng).
    static Set<BookingStatus> WITHDRAWABLE_BOOKING_STATUSES = EnumSet.of(BookingStatus.CONFIRMED,
            BookingStatus.IN_PROGRESS);
    static String PERSONAL_REASON_LABEL = "Lý do cá nhân";
    static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    CaregiverWithdrawalRepository caregiverWithdrawalRepository;
    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingEventRepository bookingEventRepository;
    BookingEventMapper bookingEventMapper;
    CaregiverWithdrawalMapper caregiverWithdrawalMapper;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    BookingProperties bookingProperties;
    CaregiverAssignmentService caregiverAssignmentService;
    TransactionTemplate transactionTemplate;
    Clock clock;

    // Danh sách lần caregiver rút khỏi buổi theo phạm vi người gọi.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverWithdrawalResponse>> list(UUID actorId, Set<RoleCode> roles,
                                                                       UUID scopeCenterId, UUID bookingId,
                                                                       boolean pendingOnly, int page, int size) {
        PageRequest pageRequest = PageRequests.unsorted(page, size);
        Instant now = clock.instant();
        Page<CaregiverWithdrawal> withdrawals;
        if (roles.contains(RoleCode.CENTER_MANAGER)) {
            if (scopeCenterId == null) {
                throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
            }
            withdrawals = caregiverWithdrawalRepository.findCenterWithdrawals(scopeCenterId, bookingId, pendingOnly,
                    OccurrenceStatus.NOT_STARTED, now, pageRequest);
        } else {
            withdrawals = caregiverWithdrawalRepository.findCaregiverWithdrawals(actorId, bookingId, pageRequest);
        }
        PageResponse<CaregiverWithdrawalResponse> withdrawalResponses = PageResponse.of(withdrawals,
                caregiverWithdrawalMapper.toResponseList(withdrawals.getContent(), now));
        return APIResponse.success("Lấy danh sách yêu cầu hủy buổi thành công", withdrawalResponses);
    }

    // Caregiver rút khỏi một buổi hoặc cả ngày; buổi bị rút chờ phân công lại.
    @Override
    public APIResponse<List<CaregiverWithdrawalResponse>> create(UUID actorId, CaregiverWithdrawalRequest request) {
        String reasonDetail = request.getReasonDetail() == null || request.getReasonDetail().isBlank()
                ? null
                : request.getReasonDetail().strip();
        if (request.getReasonCode() == CaregiverWithdrawalReason.OTHER && reasonDetail == null) {
            throw new AppException(ErrorCode.WITHDRAWAL_REASON_DETAIL_REQUIRED);
        }
        Set<UUID> occurrenceIds = new LinkedHashSet<>(request.getOccurrenceIds());
        UUID bookingId = findCommonBooking(actorId, occurrenceIds);
        List<CaregiverWithdrawal> savedWithdrawals = transactionTemplate.execute(status -> {
            // Thứ tự khóa booking → buổi như mọi luồng đổi caregiver / hủy buổi.
            Booking booking = bookingRepository.findForUpdate(bookingId)
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            if (!WITHDRAWABLE_BOOKING_STATUSES.contains(booking.getStatus())) {
                throw new AppException(ErrorCode.WITHDRAWAL_NOT_ALLOWED);
            }
            Instant now = clock.instant();
            Instant earliestStart = now.plus(bookingProperties.getSchedule().getCaregiverWithdrawalLead());
            List<BookingOccurrence> targets = new ArrayList<>();
            for (UUID occurrenceId : occurrenceIds) {
                BookingOccurrence occurrence = bookingOccurrenceRepository.findForUpdate(occurrenceId)
                        .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
                checkWithdrawable(actorId, occurrence, earliestStart);
                targets.add(occurrence);
            }
            checkWholeDays(actorId, bookingId, targets, occurrenceIds);
            String eventReason = describeReason(request.getReasonCode(), reasonDetail);
            List<CaregiverWithdrawal> withdrawals = new ArrayList<>();
            for (BookingOccurrence occurrence : targets) {
                withdrawals.add(caregiverWithdrawalMapper.toCaregiverWithdrawal(occurrence, request, reasonDetail));
                occurrence.setNeedsReassignment(true);
                bookingEventRepository.save(bookingEventMapper.toStatusEvent(occurrence,
                        BookingEventType.CAREGIVER_WITHDRAWN, occurrence.getStatus(), occurrence.getStatus(), actorId,
                        eventReason, now));
            }
            return caregiverWithdrawalRepository.saveAll(withdrawals);
        });
        notifyWithdrawn(savedWithdrawals, request.getReasonCode(), reasonDetail);
        log.info("Caregiver {} withdrew from {} occurrence(s) of booking {}", actorId, savedWithdrawals.size(), bookingId);
        // Yêu cầu B: thử tìm người thay ngay; lỗi / không có người thì scheduler thử lại và báo trung tâm phân tay.
        try {
            caregiverAssignmentService.autoAssign(bookingId);
        } catch (RuntimeException e) {
            log.warn("Immediate auto-assign after withdrawal failed for booking {} ({})", bookingId,
                    e.getClass().getSimpleName());
        }
        List<CaregiverWithdrawalResponse> withdrawalResponses = caregiverWithdrawalMapper.toResponseList(savedWithdrawals,
                clock.instant());
        return APIResponse.success("Đã gửi yêu cầu hủy buổi, trung tâm sẽ phân người khác", withdrawalResponses);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    // Mọi buổi phải là của caregiver này và cùng một booking; buổi người khác / không tồn tại → 404 (không lộ tồn tại).
    private UUID findCommonBooking(UUID actorId, Set<UUID> occurrenceIds) {
        UUID bookingId = null;
        for (UUID occurrenceId : occurrenceIds) {
            BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(occurrenceId)
                    .filter(found -> actorId.equals(found.getCaregiverUserId()))
                    .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
            UUID occurrenceBookingId = occurrence.getBooking().getId();
            if (bookingId != null && !bookingId.equals(occurrenceBookingId)) {
                throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
            }
            bookingId = occurrenceBookingId;
        }
        return bookingId;
    }

    // Kiểm lại dưới khóa: vẫn của caregiver này, đã lên lịch (đã nhận hoặc chưa nhận), còn hơn lead (24h), chưa xin hủy.
    private void checkWithdrawable(UUID actorId, BookingOccurrence occurrence, Instant earliestStart) {
        if (!actorId.equals(occurrence.getCaregiverUserId())) {
            throw new AppException(ErrorCode.OCCURRENCE_NOT_FOUND);
        }
        if (!OccurrenceStatus.WORKABLE.contains(occurrence.getStatus())) {
            throw new AppException(ErrorCode.WITHDRAWAL_NOT_ALLOWED);
        }
        if (!occurrence.getStartAt().isAfter(earliestStart)) {
            throw new AppException(ErrorCode.WITHDRAWAL_TOO_LATE);
        }
        if (caregiverWithdrawalRepository.existsByOccurrenceIdAndCaregiverIdAndDeletedAtIsNull(occurrence.getId(),
                occurrence.getCaregiverId())) {
            throw new AppException(ErrorCode.WITHDRAWAL_ALREADY_REQUESTED);
        }
    }

    // 2026-11-10: một ngày có nhiều buổi (thường là nhiều dịch vụ nối nhau) thì phải xin hủy cả ngày — hủy lẻ một buổi
    // làm khách nhận hai người chăm sóc khác nhau trong cùng một ngày. Buổi đã xin hủy trước (chờ người thay) không tính.
    private void checkWholeDays(UUID actorId, UUID bookingId, List<BookingOccurrence> targets,
                                Set<UUID> occurrenceIds) {
        ZoneId timezone = bookingProperties.getSchedule().getTimezone();
        Set<LocalDate> days = new HashSet<>();
        for (BookingOccurrence target : targets) {
            days.add(target.getStartAt().atZone(timezone).toLocalDate());
        }
        for (BookingOccurrence sibling : bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(bookingId)) {
            boolean sameDay = days.contains(sibling.getStartAt().atZone(timezone).toLocalDate());
            boolean stillMine = actorId.equals(sibling.getCaregiverUserId())
                    && OccurrenceStatus.WORKABLE.contains(sibling.getStatus())
                    && !Boolean.TRUE.equals(sibling.getNeedsReassignment());
            if (sameDay && stillMine && !occurrenceIds.contains(sibling.getId())) {
                throw new AppException(ErrorCode.WITHDRAWAL_WHOLE_DAY_REQUIRED);
            }
        }
    }

    // Mô tả lý do rút (kèm chi tiết khi là lý do cá nhân).
    private String describeReason(CaregiverWithdrawalReason reasonCode, String reasonDetail) {
        if (reasonCode == CaregiverWithdrawalReason.PERSONAL) {
            return reasonDetail == null ? PERSONAL_REASON_LABEL : PERSONAL_REASON_LABEL + ": " + reasonDetail;
        }
        return reasonDetail;
    }

    // Trung tâm: cần phân người khác (kèm lý do). Khách: chỉ báo trung tính, không nêu lý do riêng của caregiver (Q4);
    // khi trung tâm phân người mới, API đổi caregiver báo khách như cũ.
    private void notifyWithdrawn(List<CaregiverWithdrawal> withdrawals, CaregiverWithdrawalReason reasonCode,
                                 String reasonDetail) {
        Booking booking = withdrawals.getFirst().getBooking();
        Instant firstStart = withdrawals.stream()
                .map(withdrawal -> withdrawal.getOccurrence().getStartAt())
                .min(Comparator.naturalOrder())
                .orElseThrow();
        String centerBody = "Người chăm sóc xin hủy " + withdrawals.size() + " buổi của lịch " + booking.getBookingCode()
                + " (" + describeReason(reasonCode, reasonDetail) + ") — cần phân người khác, buổi đầu "
                + TIME_FORMAT.format(firstStart.atZone(bookingProperties.getSchedule().getTimezone()));
        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CAREGIVER_UNASSIGNED,
                Map.of("body", centerBody), "BOOKING", booking.getId());
        String customerBody = "Trung tâm đang sắp xếp người chăm sóc khác cho " + withdrawals.size()
                + " buổi của lịch " + booking.getBookingCode() + ", bạn sẽ được báo khi có người thay";
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.CAREGIVER_UNASSIGNED,
                Map.of("body", customerBody), "BOOKING", booking.getId());
    }
}
