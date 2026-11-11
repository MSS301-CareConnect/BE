package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.ProviderCaregiverCardResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.mapper.BookingEventMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.projection.CaregiverLoadView;
import mss301.bookingservice.repository.CaregiverWithdrawalRepository;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.CaregiverAssignmentService;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.CaregiverScheduleGuard;
import mss301.bookingservice.service.NotificationService;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverAssignmentServiceImpl implements CaregiverAssignmentService {

    static String AUTO_ASSIGN_REASON = "Hệ thống tự phân công";
    // Khoảng quanh lịch dùng để tính "ít việc" của ứng viên.
    static Duration LOAD_WINDOW = Duration.ofDays(7);

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingDetailRepository bookingDetailRepository;
    BookingEventRepository bookingEventRepository;
    CaregiverWithdrawalRepository caregiverWithdrawalRepository;
    BookingEventMapper bookingEventMapper;
    OccurrenceMapper occurrenceMapper;
    CaregiverPort caregiverPort;
    CaregiverScheduleGuard caregiverScheduleGuard;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    BookingProperties bookingProperties;
    TransactionTemplate transactionTemplate;
    Clock clock;

    // Xếp hạng caregiver rảnh có đủ kỹ năng theo tải công việc.
    @Override
    public List<AssignedCaregiver> rankCandidates(UUID centerId, Collection<UUID> careServiceIds,
                                                  List<OccurrenceSlot> slots, Set<UUID> excludedCaregiverIds) {
        if (slots.isEmpty() || careServiceIds.isEmpty()) {
            return List.of();
        }
        List<ProviderCaregiverCardResponse> cards = caregiverPort.findFreeCaregivers(centerId, careServiceIds, slots).stream()
                .filter(card -> centerId.equals(card.getCenterId()))
                .filter(card -> !excludedCaregiverIds.contains(card.getCaregiverId()))
                .toList();
        if (cards.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> loads = findLoads(cards, slots);
        List<ProviderCaregiverCardResponse> ranked = cards.stream()
                .sorted(Comparator.<ProviderCaregiverCardResponse>comparingLong(card -> loads.getOrDefault(card.getCaregiverId(), 0L))
                        .thenComparing(card -> card.getRatingAverage() == null ? BigDecimal.ZERO : card.getRatingAverage(),
                                Comparator.reverseOrder())
                        .thenComparing(card -> card.getRatingCount() == null ? 0 : card.getRatingCount(),
                                Comparator.reverseOrder())
                        .thenComparing(card -> card.getExperienceYears() == null ? 0 : card.getExperienceYears(),
                                Comparator.reverseOrder())
                        .thenComparing(ProviderCaregiverCardResponse::getCaregiverId))
                .toList();
        // Hồ sơ đầy đủ (user_id Identity) chỉ có qua GET /caregivers/{id}: tra lần lượt tới khi đủ số ứng viên cần thử.
        int limit = bookingProperties.getSchedule().getAutoAssignCandidates();
        List<AssignedCaregiver> candidates = new ArrayList<>();
        for (ProviderCaregiverCardResponse card : ranked) {
            if (candidates.size() >= limit) {
                break;
            }
            try {
                candidates.add(caregiverPort.resolveAssignable(centerId, card.getCaregiverId(), careServiceIds));
            } catch (AppException e) {
                log.info("Auto-assign skips caregiver {} ({})", card.getCaregiverId(), e.getErrorCode());
            }
        }
        return candidates;
    }

    // Chọn ứng viên đầu tiên còn rảnh mọi buổi (khóa lịch từng người khi kiểm).
    @Override
    public Optional<AssignedCaregiver> pickFree(List<AssignedCaregiver> candidates, List<OccurrenceSlot> slots,
                                                UUID excludeBookingId) {
        for (AssignedCaregiver candidate : candidates) {
            caregiverScheduleGuard.lockCaregiver(candidate.getCaregiverId());
            if (caregiverScheduleGuard.isCaregiverFree(candidate.getCaregiverId(), slots, excludeBookingId)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    // Lấy id booking đã cọc tới lúc cần tự phân công.
    @Override
    public List<UUID> findAutoAssignIds() {
        Instant now = clock.instant();
        BookingProperties.Schedule schedule = bookingProperties.getSchedule();
        return bookingOccurrenceRepository.findAutoAssignBookingIds(BookingStatus.DEPOSIT_PAID,
                OccurrenceStatus.PENDING_ASSIGNMENT, OccurrenceStatus.NOT_STARTED,
                now.plus(schedule.getUnassignedCancelLead()), now.minus(schedule.getAutoAssignRetry()),
                PageRequest.of(0, schedule.getExpiryBatchSize()));
    }

    // Tự phân công caregiver cho các buổi chưa có người của booking.
    @Override
    public void autoAssign(UUID bookingId) {
        Booking booking = bookingRepository.findByIdAndDeletedAtIsNull(bookingId).orElse(null);
        if (booking == null || !BookingStatus.DEPOSIT_PAID.contains(booking.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        List<BookingOccurrence> targets = findTargets(bookingId, now);
        if (targets.isEmpty()) {
            recordAttempt(bookingId, false);
            return;
        }
        List<UUID> allServiceIds = bookingDetailRepository.findByBookingIdOrderByCreatedAtAscCareServiceIdAsc(bookingId).stream()
                .map(BookingDetail::getCareServiceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<UUID> excluded = findExcludedCaregivers(targets);
        List<Assignment> assignments = new ArrayList<>();
        try {
            // Ưu tiên một người cho mọi buổi còn thiếu của booking.
            List<AssignedCaregiver> wholeBooking = rankCandidates(booking.getCenterId(), allServiceIds,
                    toSlots(targets), excluded);
            assignInTransaction(bookingId, collectIds(targets), wholeBooking).ifPresent(assignments::add);
            if (assignments.isEmpty()) {
                assignments.addAll(assignEachOccurrence(booking, targets, allServiceIds, excluded));
            }
        } catch (AppException e) {
            // Provider / Identity lỗi: không kết luận "không có người", lượt sau thử lại.
            log.warn("Auto-assign of booking {} postponed ({})", bookingId, e.getErrorCode());
            recordAttempt(bookingId, false);
            return;
        }
        notifyAssigned(booking, assignments);
        boolean stillMissing = !findTargets(bookingId, clock.instant()).isEmpty();
        recordAttempt(bookingId, stillMissing);
        log.info("Auto-assign booking {}: {} caregiver(s) for {} occurrence(s), still missing: {}", bookingId,
                assignments.size(), assignments.stream().mapToInt(assignment -> assignment.occurrences().size()).sum(),
                stillMissing);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    // Buổi chưa bắt đầu cần người: chưa có caregiver, hoặc caregiver đã xin hủy / bị khóa. Buổi sát giờ hơn
    // unassigned-cancel-lead do job hủy + hoàn xử lý, không gán nữa.
    private List<BookingOccurrence> findTargets(UUID bookingId, Instant now) {
        Instant earliestStart = now.plus(bookingProperties.getSchedule().getUnassignedCancelLead());
        return bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(bookingId).stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()))
                .filter(occurrence -> occurrence.getStatus() == OccurrenceStatus.PENDING_ASSIGNMENT
                        || Boolean.TRUE.equals(occurrence.getNeedsReassignment()))
                .filter(occurrence -> occurrence.getStartAt().isAfter(earliestStart))
                .sorted(Comparator.comparing(BookingOccurrence::getStartAt))
                .toList();
    }

    // Người đang giữ buổi chờ thay (đã xin hủy / bị khóa) và người từng xin hủy các buổi này không được chọn lại.
    private Set<UUID> findExcludedCaregivers(List<BookingOccurrence> targets) {
        Set<UUID> excluded = new HashSet<>(caregiverWithdrawalRepository.findCaregiverIdsByOccurrenceIds(collectIds(targets)));
        targets.stream()
                .filter(occurrence -> Boolean.TRUE.equals(occurrence.getNeedsReassignment()))
                .map(BookingOccurrence::getCaregiverId)
                .filter(Objects::nonNull)
                .forEach(excluded::add);
        return excluded;
    }

    // Không ai nhận được cả booking: gán từng buổi, ưu tiên người đã nhận buổi khác của booking (ít đổi người nhất).
    private List<Assignment> assignEachOccurrence(Booking booking, List<BookingOccurrence> targets,
                                                  List<UUID> allServiceIds, Set<UUID> excluded) {
        List<Assignment> assignments = new ArrayList<>();
        Set<UUID> chosenCaregiverIds = new LinkedHashSet<>();
        for (BookingOccurrence occurrence : targets) {
            List<UUID> serviceIds = occurrence.getCareServiceId() == null
                    ? allServiceIds
                    : List.of(occurrence.getCareServiceId());
            List<AssignedCaregiver> candidates = new ArrayList<>(rankCandidates(booking.getCenterId(), serviceIds,
                    toSlots(List.of(occurrence)), excluded));
            candidates.sort(Comparator.comparing(candidate -> !chosenCaregiverIds.contains(candidate.getCaregiverId())));
            Optional<Assignment> assignment = assignInTransaction(booking.getId(), List.of(occurrence.getId()), candidates);
            assignment.ifPresent(found -> {
                chosenCaregiverIds.add(found.caregiver().getCaregiverId());
                assignments.add(found);
            });
        }
        return assignments;
    }

    // Transaction ngắn, thứ tự khóa booking → caregiver: kiểm lại buổi vẫn cần người, thử ứng viên theo thứ tự, gán.
    // Ứng viên vừa bị luồng khác giữ lịch → thử người kế tiếp; exclusion constraint vẫn chặn trùng ở DB.
    private Optional<Assignment> assignInTransaction(UUID bookingId, List<UUID> occurrenceIds,
                                                     List<AssignedCaregiver> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(transactionTemplate.execute(status -> {
                Booking lockedBooking = bookingRepository.findForUpdate(bookingId).orElse(null);
                if (lockedBooking == null || !BookingStatus.DEPOSIT_PAID.contains(lockedBooking.getStatus())) {
                    return null;
                }
                Instant now = clock.instant();
                Set<UUID> wanted = new HashSet<>(occurrenceIds);
                List<BookingOccurrence> lockedTargets = findTargets(bookingId, now).stream()
                        .filter(occurrence -> wanted.contains(occurrence.getId()))
                        .toList();
                if (lockedTargets.isEmpty()) {
                    return null;
                }
                Optional<AssignedCaregiver> chosen = pickFree(candidates, toSlots(lockedTargets), bookingId);
                if (chosen.isEmpty()) {
                    return null;
                }
                AssignedCaregiver caregiver = chosen.get();
                List<UUID> previousCaregiverUserIds = new ArrayList<>();
                for (BookingOccurrence occurrence : lockedTargets) {
                    if (occurrence.getCaregiverUserId() != null) {
                        previousCaregiverUserIds.add(occurrence.getCaregiverUserId());
                    }
                    bookingEventRepository.save(bookingEventMapper.toCaregiverChangedEvent(lockedBooking,
                            occurrence.getId(), occurrence.getCaregiverId(), caregiver.getCaregiverId(), null,
                            AUTO_ASSIGN_REASON, now));
                    occurrence.assignCaregiver(caregiver.getCaregiverId(), caregiver.getCaregiverUserId());
                }
                syncBookingCaregiver(lockedBooking, caregiver, now);
                return new Assignment(caregiver, collectIds(lockedTargets), previousCaregiverUserIds);
            }));
        } catch (DataAccessException e) {
            log.warn("Auto-assign of booking {} lost a race ({})", bookingId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // Caregiver cấp booking: chưa có thì lấy người vừa gán; mọi buổi tương lai cùng một người thì đổi theo người đó.
    private void syncBookingCaregiver(Booking booking, AssignedCaregiver caregiver, Instant now) {
        boolean allUpcomingSame = bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(booking.getId()).stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()))
                .filter(occurrence -> occurrence.getStartAt().isAfter(now))
                .allMatch(occurrence -> caregiver.getCaregiverId().equals(occurrence.getCaregiverId()));
        if (booking.getCaregiverId() == null || allUpcomingSame) {
            booking.setCaregiverId(caregiver.getCaregiverId());
            booking.setCaregiverUserId(caregiver.getCaregiverUserId());
        }
    }

    // Đếm số buổi đang nhận của từng ứng viên quanh khoảng thời gian.
    private Map<UUID, Long> findLoads(List<ProviderCaregiverCardResponse> cards, List<OccurrenceSlot> slots) {
        Instant rangeStart = slots.stream().map(OccurrenceSlot::getStartAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant rangeEnd = slots.stream().map(OccurrenceSlot::getEndAt).max(Comparator.naturalOrder()).orElseThrow();
        List<UUID> caregiverIds = cards.stream().map(ProviderCaregiverCardResponse::getCaregiverId).toList();
        return bookingOccurrenceRepository.sumCaregiverLoads(caregiverIds, rangeStart.minus(LOAD_WINDOW),
                        rangeEnd.plus(LOAD_WINDOW), OccurrenceStatus.CAREGIVER_HOLDING, BookingStatus.SCHEDULE_HOLDING).stream()
                .collect(Collectors.toMap(CaregiverLoadView::getCaregiverId,
                        view -> view.getBookedMinutes() == null ? 0L : view.getBookedMinutes(), Long::sum));
    }

    // Một thông báo mỗi người: caregiver mới (được phân N buổi), người cũ (đã có người thay), khách (đã có người chăm sóc).
    private void notifyAssigned(Booking booking, List<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        String code = booking.getBookingCode();
        Map<UUID, List<Assignment>> byCaregiver = assignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.caregiver().getCaregiverUserId()));
        byCaregiver.forEach((caregiverUserId, items) -> notificationService.notify(List.of(caregiverUserId),
                NotificationType.CAREGIVER_ASSIGNED,
                Map.of("body", "Bạn được phân " + countOccurrences(items) + " buổi của lịch " + code), "BOOKING",
                booking.getId()));
        Set<UUID> newCaregiverUserIds = byCaregiver.keySet();
        List<UUID> previous = assignments.stream()
                .flatMap(assignment -> assignment.previousCaregiverUserIds().stream())
                .distinct()
                .filter(userId -> !newCaregiverUserIds.contains(userId))
                .toList();
        if (!previous.isEmpty()) {
            notificationService.notify(previous, NotificationType.CAREGIVER_UNASSIGNED,
                    Map.of("body", "Trung tâm đã có người thay bạn cho các buổi đã xin hủy của lịch " + code), "BOOKING",
                    booking.getId());
        }
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.CAREGIVER_ASSIGNED,
                Map.of("body", "Lịch " + code + " đã có người chăm sóc cho " + countOccurrences(assignments) + " buổi"),
                "BOOKING", booking.getId());
    }

    // Ghi mốc thử; còn buổi thiếu người và đã quá unassigned-notify-every từ lần báo trước → báo trung tâm phân tay.
    private void recordAttempt(UUID bookingId, boolean stillMissing) {
        transactionTemplate.executeWithoutResult(status -> bookingRepository.findForUpdateSkipLocked(bookingId)
                .ifPresent(booking -> {
                    Instant now = clock.instant();
                    booking.setAutoAssignAttemptedAt(now);
                    Instant notifiedAt = booking.getUnassignedNotifiedAt();
                    boolean notifyDue = notifiedAt == null
                            || !notifiedAt.plus(bookingProperties.getSchedule().getUnassignedNotifyEvery()).isAfter(now);
                    if (stillMissing && notifyDue) {
                        booking.setUnassignedNotifiedAt(now);
                        bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CENTER_ACTION_DUE,
                                Map.of("body", "Chưa tìm được người chăm sóc cho lịch " + booking.getBookingCode()
                                        + " — vui lòng phân công"), "BOOKING", booking.getId());
                    }
                }));
    }

    // Đổi các buổi sang khung giờ.
    private List<OccurrenceSlot> toSlots(List<BookingOccurrence> occurrences) {
        return occurrenceMapper.toSlotList(occurrences);
    }

    // Lấy id các buổi.
    private static List<UUID> collectIds(List<BookingOccurrence> occurrences) {
        return occurrences.stream().map(BookingOccurrence::getId).toList();
    }

    // Tổng số buổi trong các lượt phân công.
    private static int countOccurrences(List<Assignment> assignments) {
        return assignments.stream().mapToInt(assignment -> assignment.occurrences().size()).sum();
    }

    // Kết quả một lần gán trong transaction (chỉ dùng nội bộ để dựng thông báo sau commit).
    private record Assignment(AssignedCaregiver caregiver, List<UUID> occurrences, List<UUID> previousCaregiverUserIds) {
    }
}
