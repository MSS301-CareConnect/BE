package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.BookableScheduleResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.CaregiverScheduleMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverScheduleService;
import mss301.providerservice.service.BookingAvailabilityService;
import mss301.providerservice.service.CaregiverService;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.util.TimeWindow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverScheduleServiceImpl implements CaregiverScheduleService {

    static int MAX_PAGE_SIZE = 100;
    static int MAX_BOOKABLE_RANGE_DAYS = 31;

    CaregiverScheduleRepository caregiverScheduleRepository;
    CareCenterRepository careCenterRepository;
    CaregiverService caregiverService;
    ProviderAuthorization providerAuthorization;
    CaregiverScheduleMapper caregiverScheduleMapper;
    BookingAvailabilityService bookingAvailabilityService;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> listMine(UUID actorId, int page, int size) {
        validatePage(page, size);
        CaregiverProfile caregiver = caregiverService.findActiveCaregiverByUser(actorId);
        Page<CaregiverSchedule> schedulePage = caregiverScheduleRepository.findByCaregiverIdAndDeletedAtIsNull(
                caregiver.getId(), PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        List<ScheduleResponse> items = caregiverScheduleMapper.toResponseList(schedulePage.getContent());
        PageResponse<ScheduleResponse> pageResponse = PageResponse.of(schedulePage, items);
        return APIResponse.success("Lấy lịch làm việc của tôi thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ScheduleResponse>> listByCenter(UUID actorId, UUID centerId, int page, int size) {
        validatePage(page, size);
        CareCenter center = careCenterRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        providerAuthorization.requireCenterManagement(center, actorId);
        Page<CaregiverSchedule> schedulePage = caregiverScheduleRepository.findByWorkShiftCenterIdAndDeletedAtIsNull(
                centerId, PageRequest.of(page, size, Sort.by("workShift.workDate").ascending()));
        List<ScheduleResponse> items = caregiverScheduleMapper.toResponseList(schedulePage.getContent());
        PageResponse<ScheduleResponse> pageResponse = PageResponse.of(schedulePage, items);
        return APIResponse.success("Lấy lịch người chăm sóc của trung tâm thành công", pageResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<ScheduleResponse> get(UUID id) {
        CaregiverSchedule schedule = caregiverScheduleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
        ScheduleResponse scheduleResponse = caregiverScheduleMapper.toResponse(schedule);
        return APIResponse.success("Lấy lịch người chăm sóc thành công", scheduleResponse);
    }

    // Chỉ lịch SCHEDULED từ hôm nay (giờ VN), tối đa 31 ngày; caregiver / trung tâm không nhận booking → 404.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<BookableScheduleResponse>> listBookable(UUID caregiverId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ProviderTime.ZONE));
        LocalDate fromDate = from == null || from.isBefore(today) ? today : from;
        LocalDate toDate = to == null ? fromDate.plusDays(MAX_BOOKABLE_RANGE_DAYS - 1L) : to;
        if (toDate.isBefore(fromDate) || ChronoUnit.DAYS.between(fromDate, toDate) + 1 > MAX_BOOKABLE_RANGE_DAYS) {
            throw new AppException(ErrorCode.AVAILABILITY_RANGE_TOO_LONG);
        }
        caregiverService.findBookableCaregiver(caregiverId);
        List<CaregiverSchedule> schedules = caregiverScheduleRepository.findByCaregiversAndWorkDateRange(
                List.of(caregiverId), ScheduleStatus.SCHEDULED, fromDate, toDate).stream()
                .sorted(Comparator.comparing((CaregiverSchedule schedule) -> schedule.getWorkShift().getWorkDate())
                        .thenComparing(schedule -> schedule.getWorkShift().getStartTime()))
                .toList();
        List<BookableScheduleResponse> scheduleResponses = caregiverScheduleMapper.toBookableResponseList(schedules);
        return APIResponse.success("Lấy lịch có thể đặt của người chăm sóc thành công", scheduleResponses);
    }

    /**
     * Chỉ còn hủy lịch (D11 — PRV-02 quy tắc 4). Không đặt @Transactional: kiểm phạm vi + trạng thái (transaction ngắn)
     * → hỏi Booking caregiver đã có buổi được giao trong khung lịch này chưa (C5.1, NGOÀI transaction) → ghi.
     */
    @Override
    public APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request) {
        if (request.getStatus() != ScheduleStatus.CANCELLED) {
            throw new AppException(ErrorCode.SCHEDULE_STATUS_INVALID);
        }
        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new AppException(ErrorCode.SCHEDULE_CANCELLATION_REASON_REQUIRED);
        }
        ScheduleSlot scheduleSlot = transactionTemplate.execute(status -> checkCancellable(actorId, id, request));
        Set<UUID> busyCaregiverIds = bookingAvailabilityService.findBusyCaregiverIds(
                List.of(scheduleSlot.caregiverId()), List.of(scheduleSlot.window()));
        if (busyCaregiverIds.contains(scheduleSlot.caregiverId())) {
            throw new AppException(ErrorCode.SCHEDULE_HAS_BOOKINGS);
        }
        ScheduleResponse scheduleResponse = transactionTemplate.execute(status -> cancelSchedule(actorId, id, request));
        return APIResponse.success("Hủy lịch thành công", scheduleResponse);
    }

    // Chạy trong transactionTemplate của updateStatus — trả caregiver + khung giờ (giờ VN → Instant) để hỏi Booking.
    private ScheduleSlot checkCancellable(UUID actorId, UUID id, ScheduleStatusRequest request) {
        CaregiverSchedule schedule = findCancellableSchedule(actorId, id, request);
        WorkShift shift = schedule.getWorkShift();
        TimeWindow window = new TimeWindow(ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime()),
                ProviderTime.toInstant(shift.getWorkDate(), shift.getEndTime()));
        return new ScheduleSlot(schedule.getCaregiver().getId(), window);
    }

    // Chạy trong transactionTemplate của updateStatus — kiểm lại vì lịch có thể vừa đổi trong lúc gọi Booking.
    private ScheduleResponse cancelSchedule(UUID actorId, UUID id, ScheduleStatusRequest request) {
        CaregiverSchedule schedule = findCancellableSchedule(actorId, id, request);
        schedule.setScheduleStatus(ScheduleStatus.CANCELLED);
        schedule.setCancellationReason(request.getReason().trim());
        schedule.setCancelledAt(clock.instant());
        schedule.getRegistration().setRegistrationStatus(RegistrationStatus.CANCELLED);
        reopenShiftWhenPossible(schedule.getWorkShift());
        CaregiverSchedule savedSchedule = caregiverScheduleRepository.save(schedule);
        log.info("Actor {} cancelled caregiver schedule {}", actorId, id);
        return caregiverScheduleMapper.toResponse(savedSchedule);
    }

    private CaregiverSchedule findCancellableSchedule(UUID actorId, UUID id, ScheduleStatusRequest request) {
        CaregiverSchedule schedule = caregiverScheduleRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
        providerAuthorization.requireCaregiverSelfOrCenterManagement(schedule.getCaregiver(), actorId);
        if (request.getVersion() == null || !request.getVersion().equals(schedule.getVersion())) {
            throw new AppException(ErrorCode.SCHEDULE_VERSION_CONFLICT);
        }
        if (schedule.getScheduleStatus() != ScheduleStatus.SCHEDULED) {
            throw new AppException(ErrorCode.SCHEDULE_STATUS_INVALID);
        }
        return schedule;
    }

    private void reopenShiftWhenPossible(WorkShift shift) {
        Instant now = clock.instant();
        boolean shiftInFuture = ProviderTime.toInstant(shift.getWorkDate(), shift.getStartTime()).isAfter(now);
        boolean registrationStillAllowed = shift.getRegistrationDeadline() == null
                || now.isBefore(shift.getRegistrationDeadline());
        if (shift.getShiftStatus() == ShiftStatus.CLOSED && shiftInFuture && registrationStillAllowed) {
            shift.setShiftStatus(ShiftStatus.OPEN);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    // Kết quả bước kiểm (transaction đọc) mang ra ngoài để gọi Booking — không mang entity ra khỏi session.
    private record ScheduleSlot(UUID caregiverId, TimeWindow window) {
    }
}
