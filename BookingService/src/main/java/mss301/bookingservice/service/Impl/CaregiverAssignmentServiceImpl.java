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
import java.util.stream.Stream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    // Gán theo độ phủ: số ứng viên ít việc nhất (mỗi nhóm dịch vụ) được hỏi lịch từng buổi.
    static int COVERAGE_CANDIDATES = 20;
    static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

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
            recordAttempt(bookingId, List.of());
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
                assignments.addAll(assignByCoverage(booking, targets, allServiceIds, excluded));
            }
        } catch (AppException e) {
            // Provider / Identity lỗi: không kết luận "không có người", lượt sau thử lại.
            log.warn("Auto-assign of booking {} postponed ({})", bookingId, e.getErrorCode());
            recordAttempt(bookingId, null);
            return;
        }
        notifyAssigned(booking, assignments);
        if (!assignments.isEmpty()) {
            warnIfSplit(booking);
        }
        List<BookingOccurrence> stillMissing = findTargets(bookingId, clock.instant());
        recordAttempt(bookingId, stillMissing);
        log.info("Auto-assign booking {}: {} caregiver(s) for {} occurrence(s), still missing: {}", bookingId,
                assignments.size(), assignments.stream().mapToInt(assignment -> assignment.occurrences().size()).sum(),
                stillMissing.size());
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

    // 2026-11-13: không ai nhận được cả booking → gán theo độ phủ: người nhận được nhiều buổi còn thiếu nhất trước (hòa:
    // ít việc hơn, đánh giá cao hơn), lặp tới khi hết buổi hoặc không ai nhận thêm — ít người trên một lịch nhất. Mỗi ứng
    // viên hỏi Provider một lần cho cả lô buổi (lịch làm việc / ngày nghỉ), trùng booking khác kiểm tại Booking.
    private List<Assignment> assignByCoverage(Booking booking, List<BookingOccurrence> targets, List<UUID> allServiceIds,
                                              Set<UUID> excluded) {
        Map<List<UUID>, List<BookingOccurrence>> groups = targets.stream()
                .collect(Collectors.groupingBy(occurrence -> occurrence.getCareServiceId() == null
                        ? allServiceIds : List.of(occurrence.getCareServiceId()), LinkedHashMap::new, Collectors.toList()));
        Map<UUID, ProviderCaregiverCardResponse> cards = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> coverage = new LinkedHashMap<>();
        Map<UUID, Long> loads = new HashMap<>();
        for (Map.Entry<List<UUID>, List<BookingOccurrence>> group : groups.entrySet()) {
            List<OccurrenceSlot> slots = toSlots(group.getValue());
            List<ProviderCaregiverCardResponse> qualified = caregiverPort.findQualifiedCaregivers(booking.getCenterId(),
                            group.getKey()).stream()
                    .filter(card -> booking.getCenterId().equals(card.getCenterId()))
                    .filter(card -> !excluded.contains(card.getCaregiverId()))
                    .toList();
            if (qualified.isEmpty()) {
                continue;
            }
            Map<UUID, Long> groupLoads = findLoads(qualified, slots);
            loads.putAll(groupLoads);
            List<ProviderCaregiverCardResponse> shortlist = qualified.stream()
                    .sorted(Comparator.comparingLong(card -> groupLoads.getOrDefault(card.getCaregiverId(), 0L)))
                    .limit(COVERAGE_CANDIDATES)
                    .toList();
            for (ProviderCaregiverCardResponse card : shortlist) {
                Set<Instant> blocked = new HashSet<>(caregiverPort.findUnavailableStarts(card.getCaregiverId(), slots));
                blocked.addAll(caregiverScheduleGuard.findBusyStarts(card.getCaregiverId(), slots, booking.getId()));
                for (BookingOccurrence occurrence : group.getValue()) {
                    if (!blocked.contains(occurrence.getStartAt())) {
                        cards.putIfAbsent(card.getCaregiverId(), card);
                        coverage.computeIfAbsent(card.getCaregiverId(), id -> new LinkedHashSet<>()).add(occurrence.getId());
                    }
                }
            }
        }
        Map<UUID, BookingOccurrence> byId = targets.stream()
                .collect(Collectors.toMap(BookingOccurrence::getId, occurrence -> occurrence));
        Set<UUID> remaining = new LinkedHashSet<>(byId.keySet());
        List<Assignment> assignments = new ArrayList<>();
        while (!remaining.isEmpty() && !coverage.isEmpty()) {
            UUID best = coverage.keySet().stream()
                    .max(Comparator.<UUID>comparingLong(id -> coverage.get(id).stream().filter(remaining::contains).count())
                            .thenComparing(id -> loads.getOrDefault(id, 0L), Comparator.reverseOrder())
                            .thenComparing(id -> cards.get(id).getRatingAverage() == null
                                    ? BigDecimal.ZERO : cards.get(id).getRatingAverage()))
                    .orElseThrow();
            List<UUID> covered = coverage.remove(best).stream().filter(remaining::contains).toList();
            if (covered.isEmpty()) {
                break;
            }
            // Dịch vụ của các buổi người này nhận (buổi không gắn dịch vụ = mọi dịch vụ của booking).
            Set<UUID> serviceIds = covered.stream()
                    .flatMap(id -> byId.get(id).getCareServiceId() == null
                            ? allServiceIds.stream() : Stream.of(byId.get(id).getCareServiceId()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            AssignedCaregiver caregiver;
            try {
                caregiver = caregiverPort.resolveAssignable(booking.getCenterId(), best, serviceIds);
            } catch (AppException e) {
                log.info("Auto-assign skips caregiver {} ({})", best, e.getErrorCode());
                continue;
            }
            assignInTransaction(booking.getId(), covered, List.of(caregiver)).ifPresent(assignment -> {
                assignments.add(assignment);
                remaining.removeAll(assignment.occurrences());
            });
        }
        return assignments;
    }

    // Lịch bị chia cho quá nhiều người (các buổi chưa bắt đầu) → báo trung tâm cân nhắc gom lại.
    private void warnIfSplit(Booking booking) {
        long caregivers = bookingOccurrenceRepository.findByBookingIdAndDeletedAtIsNullOrderByOccurrenceNoAsc(booking.getId()).stream()
                .filter(occurrence -> OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus()))
                .map(BookingOccurrence::getCaregiverId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        if (caregivers > bookingProperties.getSchedule().getSplitCaregiverWarning()) {
            bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CENTER_ACTION_DUE,
                    Map.of("body", "Lịch " + booking.getBookingCode() + " đang được chia cho " + caregivers
                            + " người chăm sóc — cân nhắc mở thêm ca để gom về ít người hơn"), "BOOKING", booking.getId());
        }
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

    // Ghi mốc thử. missing null = lượt thử hỏng (Provider lỗi) — chỉ ghi mốc. Còn buổi thiếu người: báo trung tâm lần đầu,
    // rồi mỗi khi buổi thiếu sớm nhất qua một mốc unassigned-remind-before (2026-11-13); đủ người thì xóa mốc đã báo để
    // lần thiếu sau báo ngay.
    private void recordAttempt(UUID bookingId, List<BookingOccurrence> missing) {
        transactionTemplate.executeWithoutResult(status -> bookingRepository.findForUpdateSkipLocked(bookingId)
                .ifPresent(booking -> {
                    Instant now = clock.instant();
                    booking.setAutoAssignAttemptedAt(now);
                    if (missing == null) {
                        return;
                    }
                    if (missing.isEmpty()) {
                        booking.setUnassignedNotifiedAt(null);
                        return;
                    }
                    BookingProperties.Schedule schedule = bookingProperties.getSchedule();
                    Instant earliest = missing.stream().map(BookingOccurrence::getStartAt)
                            .min(Comparator.naturalOrder()).orElseThrow();
                    Instant notifiedAt = booking.getUnassignedNotifiedAt();
                    boolean due = notifiedAt == null || schedule.getUnassignedRemindBefore().stream()
                            .map(earliest::minus)
                            .anyMatch(milestone -> milestone.isAfter(notifiedAt) && !milestone.isAfter(now));
                    if (!due) {
                        return;
                    }
                    booking.setUnassignedNotifiedAt(now);
                    DateTimeFormatter format = TIME_FORMAT.withZone(schedule.getTimezone());
                    bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.CENTER_ACTION_DUE,
                            Map.of("body", "Chưa tìm được người chăm sóc cho " + missing.size() + " buổi của lịch "
                                    + booking.getBookingCode() + ", sớm nhất " + format.format(earliest)
                                    + ". Buổi nào chưa có người lúc "
                                    + format.format(earliest.minus(schedule.getUnassignedCancelLead()))
                                    + " sẽ bị hủy và hoàn tiền — mở ca làm việc cho khung giờ này hoặc phân công"),
                            "BOOKING", booking.getId());
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
