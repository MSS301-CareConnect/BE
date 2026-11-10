package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.WorkShiftBatchRequest;
import mss301.providerservice.dto.request.WorkShiftCancelRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.request.WorkShiftStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.dto.response.WorkShiftStatisticsResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.RoleCode;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.WorkShiftMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.ShiftApprovedCount;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.BookingAvailabilityService;
import mss301.providerservice.service.WorkShiftService;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.util.ShiftTypes;
import mss301.providerservice.util.TimeWindow;
import mss301.providerservice.util.WorkShiftStates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftServiceImpl implements WorkShiftService {

    static int MAX_PAGE_SIZE = 100;
    static int MAX_CAPACITY = 50;
    static int DEFAULT_MAX_BOOKINGS = 2;
    static int MAX_BOOKINGS_PER_SHIFT = 20;
    static int MAX_NOTE_LENGTH = 500;
    static int MAX_BATCH_DATES = 31;
    static int COUNT_CHUNK = 1000;
    static Duration MIN_DURATION = Duration.ofHours(1);
    static Duration MIN_DEADLINE_LEAD = Duration.ofHours(1);
    static Set<RegistrationStatus> ACTIVE_REGISTRATIONS = EnumSet.of(RegistrationStatus.PENDING, RegistrationStatus.APPROVED);
    static String DELETED_SHIFT_REASON = "Ca làm việc đã bị xóa";

    CareCenterRepository careCenterRepository;
    WorkShiftRepository workShiftRepository;
    ShiftRegistrationRepository shiftRegistrationRepository;
    CaregiverProfileRepository caregiverProfileRepository;
    CaregiverScheduleRepository caregiverScheduleRepository;
    BookingAvailabilityService bookingAvailabilityService;
    ProviderAuthorization providerAuthorization;
    WorkShiftMapper workShiftMapper;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> create(UUID actorId, UUID centerId, WorkShiftRequest request) {
        CareCenter center = requireManagedActiveCenter(actorId, centerId);
        WorkShift savedShift = createOne(actorId, center, request);
        // Ca vừa tạo chưa có đăng ký nào.
        return APIResponse.success("Tạo ca làm việc thành công", respond(savedShift, 0, 0));
    }

    @Override
    @Transactional
    public APIResponse<List<WorkShiftResponse>> createBatch(UUID actorId, UUID centerId, WorkShiftBatchRequest request) {
        CareCenter center = requireManagedActiveCenter(actorId, centerId);
        TreeSet<LocalDate> dates = new TreeSet<>(request.getWorkDates());
        if (dates.isEmpty() || dates.size() != request.getWorkDates().size() || dates.size() > MAX_BATCH_DATES) {
            throw new AppException(ErrorCode.WORK_SHIFT_BATCH_INVALID);
        }
        List<WorkShiftResponse> created = new ArrayList<>();
        // Một ngày sai → exception → rollback cả lô: không bao giờ tạo một nửa.
        for (LocalDate date : dates) {
            Instant deadline = request.getRegistrationLeadMinutes() == null ? null
                    : ProviderTime.toInstant(date, request.getStartTime())
                    .minus(request.getRegistrationLeadMinutes(), ChronoUnit.MINUTES);
            WorkShiftRequest single = WorkShiftRequest.builder()
                    .workDate(date)
                    .startTime(request.getStartTime())
                    .endTime(request.getEndTime())
                    .shiftType(request.getShiftType())
                    .shiftStatus(request.getShiftStatus())
                    .capacity(request.getCapacity())
                    .maxBookings(request.getMaxBookings())
                    .registrationDeadline(deadline)
                    .note(request.getNote())
                    .build();
            created.add(respond(createOne(actorId, center, single), 0, 0));
        }
        return APIResponse.success("Tạo " + created.size() + " ca làm việc thành công", created);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<WorkShiftResponse>> list(UUID actorId, UUID centerId, ShiftStatus status,
                                                             LocalDate fromDate, LocalDate toDate, String shiftType,
                                                             boolean hasVacancy, boolean hasPending,
                                                             int page, int size) {
        validatePage(page, size);
        UUID scopedCenterId = resolveListCenterId(actorId, centerId);
        // Ca DRAFT là bản nháp của trung tâm — caregiver chỉ thấy ca đã công bố.
        ShiftStatus excludedStatus = isCaregiverOnly() ? ShiftStatus.DRAFT : null;
        Instant now = clock.instant();
        LocalDateTime local = LocalDateTime.ofInstant(now, ProviderTime.ZONE);
        ShiftStatus storedStatus = status == ShiftStatus.DRAFT || status == ShiftStatus.CANCELLED ? status : null;
        Sort sort = Sort.by("workDate").ascending().and(Sort.by("startTime").ascending());
        Page<WorkShift> shiftPage = workShiftRepository.searchEffective(scopedCenterId, excludedStatus, fromDate, toDate,
                blankToNull(ShiftTypes.normalize(shiftType)), storedStatus,
                status == ShiftStatus.OPEN, status == ShiftStatus.CLOSED, status == ShiftStatus.COMPLETED, hasVacancy, hasPending,
                local.toLocalDate(), local.toLocalTime(), now, PageRequest.of(page, size, sort));
        List<UUID> shiftIds = shiftPage.stream().map(WorkShift::getId).toList();
        Map<UUID, Long> approvedCounts = countByShift(shiftIds, RegistrationStatus.APPROVED);
        Map<UUID, Long> pendingCounts = countByShift(shiftIds, RegistrationStatus.PENDING);
        List<WorkShiftResponse> items = shiftPage.stream()
                .map(shift -> respond(shift, approvedCounts.getOrDefault(shift.getId(), 0L),
                        pendingCounts.getOrDefault(shift.getId(), 0L)))
                .toList();
        PageResponse<WorkShiftResponse> pageResponse = PageResponse.of(shiftPage, items);
        return APIResponse.success("Lấy danh sách ca làm việc thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WorkShiftStatisticsResponse> statistics(UUID actorId, UUID centerId, LocalDate fromDate,
                                                               LocalDate toDate, String shiftType) {
        UUID scopedCenterId = resolveListCenterId(actorId, centerId);
        if (scopedCenterId == null) {
            // ADMIN phải chọn trung tâm: số liệu luôn của một trung tâm.
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.instant();
        List<WorkShift> shifts = workShiftRepository.findForStatistics(scopedCenterId, fromDate, toDate,
                blankToNull(ShiftTypes.normalize(shiftType)));
        List<UUID> shiftIds = shifts.stream().map(WorkShift::getId).toList();
        Map<UUID, Long> approvedCounts = countByShift(shiftIds, RegistrationStatus.APPROVED);
        Map<UUID, Long> pendingCounts = countByShift(shiftIds, RegistrationStatus.PENDING);
        WorkShiftStatisticsResponse statistics = new WorkShiftStatisticsResponse();
        statistics.setTotal(shifts.size());
        for (WorkShift shift : shifts) {
            ShiftStatus effective = WorkShiftStates.effective(shift, now);
            switch (effective) {
                case DRAFT -> statistics.setDraft(statistics.getDraft() + 1);
                case OPEN -> statistics.setOpen(statistics.getOpen() + 1);
                case CLOSED -> statistics.setClosed(statistics.getClosed() + 1);
                case COMPLETED -> statistics.setCompleted(statistics.getCompleted() + 1);
                case CANCELLED -> statistics.setCancelled(statistics.getCancelled() + 1);
            }
            if (effective == ShiftStatus.OPEN || effective == ShiftStatus.CLOSED || effective == ShiftStatus.COMPLETED) {
                statistics.setCapacityTotal(statistics.getCapacityTotal() + shift.getCapacity());
                statistics.setApprovedTotal(statistics.getApprovedTotal()
                        + approvedCounts.getOrDefault(shift.getId(), 0L));
            }
            if (effective != ShiftStatus.CANCELLED && now.isBefore(WorkShiftStates.start(shift))) {
                statistics.setPendingRegistrations(statistics.getPendingRegistrations()
                        + pendingCounts.getOrDefault(shift.getId(), 0L));
            }
        }
        // "Sắp tới 7 ngày" không phụ thuộc khoảng ngày đang lọc.
        LocalDate today = LocalDate.ofInstant(now, ProviderTime.ZONE);
        Instant weekLater = now.plus(7, ChronoUnit.DAYS);
        List<WorkShift> upcomingShifts = workShiftRepository.findActiveBetween(scopedCenterId, today, today.plusDays(7))
                .stream()
                .filter(shift -> {
                    Instant start = WorkShiftStates.start(shift);
                    return !start.isBefore(now) && start.isBefore(weekLater);
                })
                .toList();
        statistics.setUpcoming7Days(upcomingShifts.size());
        // Thiếu người: ca sắp tới (cả DRAFT chưa công bố) có số APPROVED nhỏ hơn capacity.
        Map<UUID, Long> upcomingApproved = countByShift(upcomingShifts.stream().map(WorkShift::getId).toList(),
                RegistrationStatus.APPROVED);
        for (WorkShift shift : upcomingShifts) {
            long missing = shift.getCapacity() - upcomingApproved.getOrDefault(shift.getId(), 0L);
            if (missing > 0) {
                statistics.setUnderstaffed7Days(statistics.getUnderstaffed7Days() + 1);
                statistics.setMissingStaff7Days(statistics.getMissingStaff7Days() + missing);
            }
        }
        return APIResponse.success("Lấy thống kê ca làm việc thành công", statistics);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<WorkShiftResponse> get(UUID actorId, UUID id) {
        WorkShift shift = findActiveShift(id);
        checkReadScope(actorId, shift);
        return APIResponse.success("Lấy ca làm việc thành công", respondWithCounts(shift));
    }

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> update(UUID actorId, UUID id, WorkShiftRequest request) {
        WorkShift shift = findActiveShift(id);
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, request.getVersion());
        Instant now = clock.instant();
        ShiftStatus current = WorkShiftStates.effective(shift, now);
        if (current == ShiftStatus.CANCELLED || current == ShiftStatus.COMPLETED) {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        if (!now.isBefore(WorkShiftStates.start(shift))) {
            throw new AppException(ErrorCode.WORK_SHIFT_ALREADY_STARTED);
        }
        long approved = countApproved(id);
        if (approved > 0 && hasScheduleFieldsChanged(shift, request)) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        validateFields(request, shift, now);
        if (request.getCapacity() < approved) {
            throw new AppException(ErrorCode.WORK_SHIFT_FULL);
        }
        ShiftStatus target = request.getShiftStatus();
        validateTargetStatus(shift, request, target, approved, now);
        Integer previousMaxBookings = shift.getMaxBookings();
        workShiftMapper.update(request, shift);
        if (request.getMaxBookings() == null) {
            shift.setMaxBookings(previousMaxBookings != null ? previousMaxBookings : DEFAULT_MAX_BOOKINGS);
        }
        normalizeFields(shift);
        shift.setUpdatedBy(actorId);
        if (target == ShiftStatus.CANCELLED) {
            // Ca chưa có ai được duyệt (đã kiểm ở trên): hủy qua PUT chỉ còn từ chối các đăng ký đang chờ.
            markCancelled(actorId, shift, DELETED_SHIFT_REASON, now);
        }
        WorkShift savedShift = workShiftRepository.save(shift);
        return APIResponse.success("Cập nhật ca làm việc thành công",
                respond(savedShift, approved, countPending(id)));
    }

    @Override
    @Transactional
    public APIResponse<WorkShiftResponse> changeStatus(UUID actorId, UUID id, WorkShiftStatusRequest request) {
        WorkShift shift = workShiftRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, request.getVersion());
        Instant now = clock.instant();
        ShiftStatus current = WorkShiftStates.effective(shift, now);
        ShiftStatus target = request.getStatus();
        long approved = countApproved(id);
        if (target == ShiftStatus.OPEN) {
            boolean reopenable = current == ShiftStatus.DRAFT || current == ShiftStatus.OPEN
                    || current == ShiftStatus.CLOSED;
            if (!reopenable || !canOpen(shift.getRegistrationDeadline(), WorkShiftStates.start(shift),
                    shift.getCapacity(), approved, now)) {
                throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            }
            if (shift.getCenter().getCenterStatus() != CenterStatus.ACTIVE) {
                throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
            }
        } else if (target == ShiftStatus.CLOSED) {
            if (current != ShiftStatus.OPEN) {
                throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            }
        } else {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        shift.setShiftStatus(target);
        shift.setUpdatedBy(actorId);
        WorkShift savedShift = workShiftRepository.save(shift);
        return APIResponse.success("Cập nhật trạng thái ca thành công", respond(savedShift, approved, countPending(id)));
    }

    /**
     * Hủy ca trước giờ bắt đầu, kể cả khi đã có người được duyệt. Không đặt @Transactional (giống hủy lịch):
     * kiểm (transaction ngắn) → hỏi Booking các caregiver được duyệt đã có buổi trong khung ca chưa (NGOÀI
     * transaction) → ghi: lịch SCHEDULED → CANCELLED, đăng ký APPROVED → CANCELLED, PENDING → REJECTED (lý do của ca).
     */
    @Override
    public APIResponse<WorkShiftResponse> cancel(UUID actorId, UUID id, WorkShiftCancelRequest request) {
        CancelCheck check = transactionTemplate.execute(status -> checkCancellable(actorId, id, request.getVersion()));
        if (check != null && !check.caregiverIds().isEmpty()) {
            Set<UUID> busy = bookingAvailabilityService.findBusyCaregiverIds(check.caregiverIds(),
                    List.of(check.window()));
            if (!busy.isEmpty()) {
                throw new AppException(ErrorCode.WORK_SHIFT_HAS_BOOKINGS);
            }
        }
        WorkShiftResponse response = transactionTemplate.execute(status -> applyCancel(actorId, id, request, check));
        return APIResponse.success("Hủy ca làm việc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<Void> delete(UUID actorId, UUID id, Long version) {
        WorkShift shift = findActiveShift(id);
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, version);
        if (countApproved(id) > 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
        }
        Instant now = clock.instant();
        markCancelled(actorId, shift, DELETED_SHIFT_REASON, now);
        shift.setDeletedAt(now);
        shift.setUpdatedBy(actorId);
        workShiftRepository.save(shift);
        return APIResponse.success("Hủy ca làm việc thành công");
    }

    @Override
    public WorkShift findActiveShift(UUID id) {
        return workShiftRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
    }

    @Override
    public long countApproved(UUID shiftId) {
        return shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shiftId, RegistrationStatus.APPROVED);
    }

    private long countPending(UUID shiftId) {
        return shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shiftId, RegistrationStatus.PENDING);
    }

    private CareCenter requireManagedActiveCenter(UUID actorId, UUID centerId) {
        CareCenter center = careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        providerAuthorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        return center;
    }

    private WorkShift createOne(UUID actorId, CareCenter center, WorkShiftRequest request) {
        Instant now = clock.instant();
        validateFields(request, null, now);
        // Khi tạo chỉ nhận DRAFT hoặc OPEN; các trạng thái khác chỉ tới bằng action / job.
        if (request.getShiftStatus() != ShiftStatus.DRAFT && request.getShiftStatus() != ShiftStatus.OPEN) {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        WorkShift shift = workShiftMapper.toWorkShift(request, center);
        normalizeFields(shift);
        if (shift.getRegistrationDeadline() == null) {
            shift.setRegistrationDeadline(WorkShiftStates.defaultDeadline(WorkShiftStates.start(shift), now));
        }
        shift.setCreatedBy(actorId);
        shift.setUpdatedBy(actorId);
        return workShiftRepository.save(shift);
    }

    private WorkShiftResponse respondWithCounts(WorkShift shift) {
        return respond(shift, countApproved(shift.getId()), countPending(shift.getId()));
    }

    // Mọi response ca đi qua đây: trạng thái trả về là trạng thái hiệu lực, không phải cột đã lưu.
    private WorkShiftResponse respond(WorkShift shift, long approved, long pending) {
        WorkShiftResponse response = workShiftMapper.toResponse(shift, approved, pending);
        response.setShiftStatus(WorkShiftStates.effective(shift, clock.instant()));
        return response;
    }

    /**
     * Phạm vi GET /work-shifts theo role (SEC-08): ADMIN mọi trung tâm (lọc theo centerId nếu có); manager chỉ trung tâm
     * mình quản lý; caregiver chỉ trung tâm của mình. Gửi centerId khác phạm vi → 403, không âm thầm đổi bộ lọc.
     */
    private UUID resolveListCenterId(UUID actorId, UUID requestedCenterId) {
        if (providerAuthorization.isAdmin()) {
            return requestedCenterId;
        }
        UUID ownCenterId;
        if (providerAuthorization.hasRole(RoleCode.CENTER_MANAGER)) {
            CareCenter center = careCenterRepository.findByManagerUserIdAndDeletedAtIsNull(actorId)
                    .orElseThrow(() -> new AppException(ErrorCode.CENTER_ACCESS_DENIED));
            ownCenterId = center.getId();
        } else if (providerAuthorization.hasRole(RoleCode.CAREGIVER)) {
            CaregiverProfile caregiver = caregiverProfileRepository.findByUserIdAndDeletedAtIsNull(actorId)
                    .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
            ownCenterId = caregiver.getCenter().getId();
        } else {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (requestedCenterId != null && !requestedCenterId.equals(ownCenterId)) {
            throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
        }
        return ownCenterId;
    }

    /**
     * Cùng phạm vi với GET /work-shifts: ca ngoài phạm vi (trung tâm khác, hoặc DRAFT với caregiver) trả 404 như không
     * tồn tại — không lộ ca nháp / ca của trung tâm khác qua đoán id.
     */
    private void checkReadScope(UUID actorId, WorkShift shift) {
        if (providerAuthorization.isAdmin()) {
            return;
        }
        UUID ownCenterId;
        try {
            ownCenterId = resolveListCenterId(actorId, null);
        } catch (AppException exception) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND);
        }
        boolean hiddenDraft = isCaregiverOnly() && shift.getShiftStatus() == ShiftStatus.DRAFT;
        if (!shift.getCenter().getId().equals(ownCenterId) || hiddenDraft) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND);
        }
    }

    // Caregiver thuần (không kiêm manager/admin) mới bị ẩn ca DRAFT.
    private boolean isCaregiverOnly() {
        return providerAuthorization.hasRole(RoleCode.CAREGIVER)
                && !providerAuthorization.isAdmin()
                && !providerAuthorization.hasRole(RoleCode.CENTER_MANAGER);
    }

    // Một query gom nhóm cho cả danh sách thay vì một count mỗi ca (N+1); chia lô để mệnh đề IN không quá dài.
    private Map<UUID, Long> countByShift(List<UUID> shiftIds, RegistrationStatus status) {
        Map<UUID, Long> counts = new HashMap<>();
        for (int from = 0; from < shiftIds.size(); from += COUNT_CHUNK) {
            Collection<UUID> chunk = shiftIds.subList(from, Math.min(shiftIds.size(), from + COUNT_CHUNK));
            for (ShiftApprovedCount count : shiftRegistrationRepository.countByShiftIds(chunk, status)) {
                counts.put(count.getShiftId(), count.getApprovedCount());
            }
        }
        return counts;
    }

    // shiftType lưu dạng chữ hoa để lọc/so sánh ổn định; note rỗng → null.
    private void normalizeFields(WorkShift shift) {
        shift.setShiftType(ShiftTypes.normalize(shift.getShiftType()));
        shift.setNote(blankToNull(shift.getNote()));
        if (shift.getMaxBookings() == null) {
            shift.setMaxBookings(DEFAULT_MAX_BOOKINGS);
        }
    }

    /**
     * Quy tắc tạo / sửa ca. {@code existing == null} là tạo mới; khi sửa, một quy tắc chỉ áp lên giá trị thực sự đổi —
     * ca cũ (loại DAILY, sức chứa &gt; 50, ghi chú dài) vẫn sửa được các trường khác.
     */
    private void validateFields(WorkShiftRequest request, WorkShift existing, Instant now) {
        boolean creating = existing == null;
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new AppException(ErrorCode.WORK_SHIFT_TIME_INVALID);
        }
        boolean timeChanged = creating || hasScheduleFieldsChanged(existing, request);
        if (timeChanged && Duration.between(request.getStartTime(), request.getEndTime()).compareTo(MIN_DURATION) < 0) {
            throw new AppException(ErrorCode.WORK_SHIFT_DURATION_INVALID);
        }
        // Giờ ca là giờ Việt Nam: so với "bây giờ" theo cùng múi giờ, không theo UTC (lệch 7 giờ).
        Instant shiftStart = ProviderTime.toInstant(request.getWorkDate(), request.getStartTime());
        if (timeChanged && !shiftStart.isAfter(now)) {
            throw new AppException(ErrorCode.WORK_SHIFT_DATE_INVALID);
        }
        String type = ShiftTypes.normalize(request.getShiftType());
        if ((creating || !Objects.equals(type, existing.getShiftType())) && !ShiftTypes.isKnown(type)) {
            throw new AppException(ErrorCode.WORK_SHIFT_TYPE_INVALID);
        }
        boolean capacityChanged = creating || !Objects.equals(request.getCapacity(), existing.getCapacity());
        if (request.getMaxBookings() != null
                && (request.getMaxBookings() < 1 || request.getMaxBookings() > MAX_BOOKINGS_PER_SHIFT)) {
            throw new AppException(ErrorCode.WORK_SHIFT_CAPACITY_INVALID);
        }
        if (capacityChanged && (request.getCapacity() < 1 || request.getCapacity() > MAX_CAPACITY)) {
            throw new AppException(ErrorCode.WORK_SHIFT_CAPACITY_INVALID);
        }
        String note = blankToNull(request.getNote());
        boolean noteChanged = creating || !Objects.equals(note, existing.getNote());
        if (noteChanged && note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new AppException(ErrorCode.WORK_SHIFT_NOTE_TOO_LONG);
        }
        Instant deadline = request.getRegistrationDeadline();
        if (deadline == null) {
            return;
        }
        boolean deadlineChanged = creating || !deadline.equals(existing.getRegistrationDeadline());
        if (deadlineChanged && (!deadline.isAfter(now) || deadline.isAfter(shiftStart.minus(MIN_DEADLINE_LEAD)))) {
            throw new AppException(ErrorCode.WORK_SHIFT_DEADLINE_INVALID);
        }
        // Hạn cũ giữ nguyên nhưng giờ ca dời sớm hơn: hạn vẫn phải trước giờ bắt đầu.
        if (!deadline.isBefore(shiftStart)) {
            throw new AppException(ErrorCode.WORK_SHIFT_DEADLINE_INVALID);
        }
    }

    // PUT không được đưa ca vào trạng thái sai: mở lại ca đã qua / quá hạn / đủ người, về nháp khi đã có đăng ký.
    private void validateTargetStatus(WorkShift shift, WorkShiftRequest request, ShiftStatus target, long approved,
                                      Instant now) {
        switch (target) {
            case COMPLETED -> throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
            case CANCELLED -> {
                if (approved > 0) {
                    throw new AppException(ErrorCode.WORK_SHIFT_HAS_APPROVED_REGISTRATIONS);
                }
            }
            case DRAFT -> {
                boolean hasRegistrations = !shiftRegistrationRepository
                        .findByWorkShiftIdAndRegistrationStatusInAndDeletedAtIsNull(shift.getId(), ACTIVE_REGISTRATIONS)
                        .isEmpty();
                if (shift.getShiftStatus() != ShiftStatus.DRAFT && hasRegistrations) {
                    throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
                }
            }
            case OPEN -> {
                Instant start = ProviderTime.toInstant(request.getWorkDate(), request.getStartTime());
                if (!canOpen(request.getRegistrationDeadline(), start, request.getCapacity(), approved, now)) {
                    throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
                }
            }
            case CLOSED -> {
                // Đóng đăng ký thủ công luôn được.
            }
        }
    }

    private boolean canOpen(Instant deadline, Instant start, int capacity, long approved, Instant now) {
        return start.isAfter(now) && (deadline == null || deadline.isAfter(now)) && approved < capacity;
    }

    // Chạy trong transactionTemplate của cancel — trả các caregiver đang có lịch của ca + khung giờ ca để hỏi Booking.
    private CancelCheck checkCancellable(UUID actorId, UUID id, Long version) {
        WorkShift shift = findActiveShift(id);
        providerAuthorization.requireCenterManagement(shift.getCenter(), actorId);
        checkVersion(shift, version);
        Instant now = clock.instant();
        ShiftStatus current = WorkShiftStates.effective(shift, now);
        if (current == ShiftStatus.CANCELLED || current == ShiftStatus.COMPLETED) {
            throw new AppException(ErrorCode.WORK_SHIFT_STATUS_INVALID);
        }
        if (!now.isBefore(WorkShiftStates.start(shift))) {
            throw new AppException(ErrorCode.WORK_SHIFT_ALREADY_STARTED);
        }
        Set<UUID> caregiverIds = new HashSet<>();
        for (CaregiverSchedule schedule : scheduledOf(id)) {
            caregiverIds.add(schedule.getCaregiver().getId());
        }
        return new CancelCheck(caregiverIds, new TimeWindow(WorkShiftStates.start(shift), WorkShiftStates.end(shift)));
    }

    // Chạy trong transactionTemplate của cancel — khóa ca, kiểm lại vì có thể vừa duyệt thêm người trong lúc hỏi Booking.
    private WorkShiftResponse applyCancel(UUID actorId, UUID id, WorkShiftCancelRequest request, CancelCheck check) {
        WorkShift shift = workShiftRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.WORK_SHIFT_NOT_FOUND));
        checkVersion(shift, request.getVersion());
        Instant now = clock.instant();
        if (!now.isBefore(WorkShiftStates.start(shift))) {
            throw new AppException(ErrorCode.WORK_SHIFT_ALREADY_STARTED);
        }
        String reason = request.getReason().trim();
        for (CaregiverSchedule schedule : scheduledOf(id)) {
            if (check == null || !check.caregiverIds().contains(schedule.getCaregiver().getId())) {
                throw new AppException(ErrorCode.WORK_SHIFT_VERSION_CONFLICT);
            }
            schedule.setScheduleStatus(ScheduleStatus.CANCELLED);
            schedule.setCancellationReason(reason);
            schedule.setCancelledAt(now);
            schedule.getRegistration().setRegistrationStatus(RegistrationStatus.CANCELLED);
            caregiverScheduleRepository.save(schedule);
        }
        markCancelled(actorId, shift, reason, now);
        shift.setUpdatedBy(actorId);
        WorkShift savedShift = workShiftRepository.save(shift);
        log.info("Actor {} cancelled work shift {}", actorId, id);
        return respond(savedShift, 0, 0);
    }

    // Ca → CANCELLED kèm lý do; đăng ký đang chờ → REJECTED với cùng lý do để caregiver biết vì sao.
    private void markCancelled(UUID actorId, WorkShift shift, String reason, Instant now) {
        shift.setShiftStatus(ShiftStatus.CANCELLED);
        shift.setCancellationReason(reason);
        shift.setCancelledAt(now);
        for (ShiftRegistration registration : shiftRegistrationRepository
                .findByWorkShiftIdAndRegistrationStatusInAndDeletedAtIsNull(shift.getId(),
                        EnumSet.of(RegistrationStatus.PENDING))) {
            registration.setRegistrationStatus(RegistrationStatus.REJECTED);
            registration.setRejectionReason(reason);
            registration.setReviewedBy(actorId);
            registration.setReviewedAt(now);
            shiftRegistrationRepository.save(registration);
        }
    }

    private List<CaregiverSchedule> scheduledOf(UUID shiftId) {
        return caregiverScheduleRepository.findByWorkShiftIdAndScheduleStatusAndDeletedAtIsNull(shiftId,
                ScheduleStatus.SCHEDULED);
    }

    private boolean hasScheduleFieldsChanged(WorkShift shift, WorkShiftRequest request) {
        return !shift.getWorkDate().equals(request.getWorkDate())
                || !shift.getStartTime().equals(request.getStartTime())
                || !shift.getEndTime().equals(request.getEndTime());
    }

    private void checkVersion(WorkShift shift, Long version) {
        if (version == null || !version.equals(shift.getVersion())) {
            throw new AppException(ErrorCode.WORK_SHIFT_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // Kết quả bước kiểm (transaction đọc) mang ra ngoài để gọi Booking — không mang entity ra khỏi session.
    private record CancelCheck(Set<UUID> caregiverIds, TimeWindow window) {
    }
}
