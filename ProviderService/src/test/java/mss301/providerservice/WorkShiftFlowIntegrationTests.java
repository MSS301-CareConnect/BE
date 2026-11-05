package mss301.providerservice;

import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.CaregiverTimeOff;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.service.BookingAvailabilityService;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.service.WorkShiftLifecycleService;
import mss301.providerservice.util.ProviderTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng ca làm việc 2026-11-05: trạng thái hiệu lực, job vòng đời, validate tạo / sửa, hủy ca dây chuyền, kiểm trùng
 * khi đăng ký, thống kê, duyệt hàng loạt và tạo lặp lại. H2 + repository thật; Booking (busy-caregivers) giả lập.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkShiftFlowIntegrationTests {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:provider_shift_flow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BookingAvailabilityService bookingAvailabilityService;

    @Autowired
    CareCenterRepository careCenterRepository;

    @Autowired
    CaregiverProfileRepository caregiverProfileRepository;

    @Autowired
    WorkShiftRepository workShiftRepository;

    @Autowired
    ShiftRegistrationRepository shiftRegistrationRepository;

    @Autowired
    CaregiverScheduleRepository caregiverScheduleRepository;

    @Autowired
    CaregiverTimeOffRepository caregiverTimeOffRepository;

    @Autowired
    ShiftRegistrationService shiftRegistrationService;

    @Autowired
    WorkShiftLifecycleService workShiftLifecycleService;

    @Test
    void openFilterAndStatusUseTheEffectiveStateNeverAPastShift() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        WorkShift open = saveShift(center, today().plusDays(3), 8, 12, 2);
        WorkShift deadlinePassed = saveShift(center, today().plusDays(3), 13, 17, 2);
        deadlinePassed.setRegistrationDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        workShiftRepository.save(deadlinePassed);
        WorkShift past = saveShift(center, today().minusDays(1), 8, 12, 2);

        mockMvc.perform(get("/api/v1/work-shifts").param("status", "OPEN").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(open.getId().toString()));
        mockMvc.perform(get("/api/v1/work-shifts").param("status", "CLOSED").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(deadlinePassed.getId().toString()));
        mockMvc.perform(get("/api/v1/work-shifts").param("status", "COMPLETED")
                        .with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].shiftStatus").value("COMPLETED"));
        mockMvc.perform(get("/api/v1/work-shifts/{id}", past.getId()).with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.shiftStatus").value("COMPLETED"));
        mockMvc.perform(get("/api/v1/work-shifts").param("hasVacancy", "true")
                        .with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.totalElements").value(3));
    }

    @Test
    void lifecycleJobStoresTheEffectiveStateAndRejectsPendingOfStartedShifts() {
        CareCenter center = saveCenter(UUID.randomUUID());
        WorkShift past = saveShift(center, today().minusDays(1), 8, 12, 2);
        WorkShift expired = saveShift(center, today().plusDays(2), 8, 12, 2);
        expired.setRegistrationDeadline(Instant.now().minus(5, ChronoUnit.MINUTES));
        workShiftRepository.save(expired);
        ShiftRegistration pending = shiftRegistrationRepository.save(ShiftRegistration.builder()
                .workShift(past)
                .caregiver(saveCaregiver(center))
                .registrationStatus(RegistrationStatus.PENDING)
                .build());

        workShiftLifecycleService.advance();

        assertThat(workShiftRepository.findById(past.getId()).orElseThrow().getShiftStatus())
                .isEqualTo(ShiftStatus.COMPLETED);
        assertThat(workShiftRepository.findById(expired.getId()).orElseThrow().getShiftStatus())
                .isEqualTo(ShiftStatus.CLOSED);
        ShiftRegistration rejected = shiftRegistrationRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getRegistrationStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isNotBlank();
    }

    @Test
    void createValidatesEveryFieldWithItsOwnCodeAndDefaultsTheDeadline() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        LocalDate day = today().plusDays(4);
        String url = "/api/v1/work-shifts/centers/" + center.getId();

        expectCode(post(url), managerId, shiftBody(day, "08:00", "12:00", "MORNING", "OPEN", 51, null, null),
                ErrorCode.WORK_SHIFT_CAPACITY_INVALID);
        expectCode(post(url), managerId, shiftBody(day, "08:00", "12:00", "DAILY", "OPEN", 2, null, null),
                ErrorCode.WORK_SHIFT_TYPE_INVALID);
        expectCode(post(url), managerId, shiftBody(day, "08:00", "12:00", "MORNING", "OPEN", 2, null, "x".repeat(501)),
                ErrorCode.WORK_SHIFT_NOTE_TOO_LONG);
        expectCode(post(url), managerId, shiftBody(day, "08:00", "08:30", "MORNING", "OPEN", 2, null, null),
                ErrorCode.WORK_SHIFT_DURATION_INVALID);
        expectCode(post(url), managerId, shiftBody(day, "08:00", "12:00", "MORNING", "OPEN", 2,
                vn(day, 7).plus(30, ChronoUnit.MINUTES), null), ErrorCode.WORK_SHIFT_DEADLINE_INVALID);
        expectCode(post(url), managerId, shiftBody(day, "08:00", "12:00", "MORNING", "CLOSED", 2, null, null),
                ErrorCode.WORK_SHIFT_STATUS_INVALID);
        expectCode(post(url), managerId, shiftBody(today().minusDays(1), "08:00", "12:00", "MORNING", "OPEN", 2,
                null, null), ErrorCode.WORK_SHIFT_DATE_INVALID);

        mockMvc.perform(post(url).with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shiftBody(day, "08:00", "12:00", " morning ", "OPEN", 2, null, "  Mang đồ bảo hộ  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shiftType").value("MORNING"))
                .andExpect(jsonPath("$.result.note").value("Mang đồ bảo hộ"))
                .andExpect(jsonPath("$.result.registrationDeadline").value(vn(day, 8).minus(24, ChronoUnit.HOURS)
                        .toString()));
    }

    @Test
    void updateKeepsLegacyTypeButNeverReopensAFullShift() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        LocalDate day = today().plusDays(5);
        WorkShift legacy = saveShift(center, day, 8, 12, 1);
        legacy.setShiftType("DAILY");
        legacy = workShiftRepository.save(legacy);
        ShiftRegistrationResponse registration = register(saveCaregiver(center), legacy);
        approve(managerId, registration.getId());
        WorkShift full = workShiftRepository.findById(legacy.getId()).orElseThrow();
        assertThat(full.getShiftStatus()).isEqualTo(ShiftStatus.CLOSED);

        expectCode(put("/api/v1/work-shifts/" + full.getId()), managerId,
                shiftBody(day, "08:00", "12:00", "DAILY", "OPEN", 1, null, null, full.getVersion()),
                ErrorCode.WORK_SHIFT_STATUS_INVALID);
        mockMvc.perform(put("/api/v1/work-shifts/" + full.getId()).with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(shiftBody(day, "08:00", "12:00", "DAILY", "OPEN", 2, null, "Thêm chỗ",
                                full.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shiftType").value("DAILY"))
                .andExpect(jsonPath("$.result.shiftStatus").value("OPEN"))
                .andExpect(jsonPath("$.result.capacity").value(2));
    }

    @Test
    void cancelCascadesToRegistrationsAndSchedulesUnlessACaregiverIsBooked() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        WorkShift shift = saveShift(center, today().plusDays(3), 8, 12, 3);
        CaregiverProfile approvedCaregiver = saveCaregiver(center);
        ShiftRegistrationResponse approved = register(approvedCaregiver, shift);
        approve(managerId, approved.getId());
        ShiftRegistrationResponse pending = register(saveCaregiver(center), shift);
        long version = workShiftRepository.findById(shift.getId()).orElseThrow().getVersion();

        when(bookingAvailabilityService.findBusyCaregiverIds(any(), anyList()))
                .thenReturn(Set.of(approvedCaregiver.getId()));
        expectCode(post("/api/v1/work-shifts/" + shift.getId() + "/cancel"), managerId,
                cancelBody("Trung tâm nghỉ lễ", version), ErrorCode.WORK_SHIFT_HAS_BOOKINGS);
        expectCode(post("/api/v1/work-shifts/" + shift.getId() + "/cancel"), managerId,
                cancelBody("  ", version), ErrorCode.WORK_SHIFT_CANCEL_REASON_REQUIRED);

        when(bookingAvailabilityService.findBusyCaregiverIds(any(), anyList())).thenReturn(Set.of());
        mockMvc.perform(post("/api/v1/work-shifts/" + shift.getId() + "/cancel").with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody("Trung tâm nghỉ lễ", version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.shiftStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.result.cancellationReason").value("Trung tâm nghỉ lễ"));

        assertThat(shiftRegistrationRepository.findById(approved.getId()).orElseThrow().getRegistrationStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);
        ShiftRegistration rejected = shiftRegistrationRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getRegistrationStatus()).isEqualTo(RegistrationStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Trung tâm nghỉ lễ");
        CaregiverSchedule schedule = caregiverScheduleRepository.findByRegistrationId(approved.getId()).orElseThrow();
        assertThat(schedule.getScheduleStatus()).isEqualTo(ScheduleStatus.CANCELLED);
    }

    @Test
    void registrationIsBlockedByTimeOffAndByAnOverlappingApprovedShift() {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center);
        LocalDate day = today().plusDays(6);
        WorkShift morning = saveShift(center, day, 8, 12, 2);
        WorkShift overlapping = saveShift(center, day, 10, 14, 2);
        approve(managerId, register(caregiver, morning).getId());

        assertThatThrownBy(() -> register(caregiver, overlapping))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_CONFLICT);

        WorkShift nextDay = saveShift(center, day.plusDays(1), 8, 12, 2);
        caregiverTimeOffRepository.save(CaregiverTimeOff.builder()
                .caregiver(caregiver)
                .startAt(vn(day.plusDays(1), 0))
                .endAt(vn(day.plusDays(2), 0))
                .createdBy(caregiver.getUserId())
                .build());
        assertThatThrownBy(() -> register(caregiver, nextDay))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.REGISTRATION_TIME_OFF_CONFLICT);
    }

    @Test
    void statisticsCountTheWholeFilterNotOnePage() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        WorkShift soon = saveShift(center, today().plusDays(2), 8, 12, 4);
        saveShift(center, today().plusDays(20), 8, 12, 2);
        WorkShift draft = saveShift(center, today().plusDays(3), 8, 12, 2);
        draft.setShiftStatus(ShiftStatus.DRAFT);
        workShiftRepository.save(draft);
        saveShift(center, today().minusDays(2), 8, 12, 2);
        register(saveCaregiver(center), soon);
        approve(managerId, register(saveCaregiver(center), soon).getId());

        mockMvc.perform(get("/api/v1/work-shifts/statistics").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.total").value(4))
                .andExpect(jsonPath("$.result.open").value(2))
                .andExpect(jsonPath("$.result.draft").value(1))
                .andExpect(jsonPath("$.result.completed").value(1))
                .andExpect(jsonPath("$.result.upcoming7Days").value(2))
                .andExpect(jsonPath("$.result.pendingRegistrations").value(1))
                .andExpect(jsonPath("$.result.approvedTotal").value(1))
                .andExpect(jsonPath("$.result.capacityTotal").value(8));
        mockMvc.perform(get("/api/v1/work-shifts").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.items[?(@.id=='" + soon.getId() + "')].pendingCount").value(1));
        mockMvc.perform(get("/api/v1/work-shifts").param("hasPending", "true").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(soon.getId().toString()));
        mockMvc.perform(get("/api/v1/work-shifts/statistics").with(user(UUID.randomUUID(), "CAREGIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkApprovalIsAllOrNothing() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        WorkShift shift = saveShift(center, today().plusDays(3), 8, 12, 1);
        ShiftRegistrationResponse first = register(saveCaregiver(center), shift);
        ShiftRegistrationResponse second = register(saveCaregiver(center), shift);
        String items = "[{\"registrationId\":\"" + first.getId() + "\",\"version\":" + first.getVersion() + "},"
                + "{\"registrationId\":\"" + second.getId() + "\",\"version\":" + second.getVersion() + "}]";

        expectCode(patch("/api/v1/work-shifts/" + shift.getId() + "/registrations/review"), managerId,
                "{\"status\":\"APPROVED\",\"items\":" + items + "}", ErrorCode.WORK_SHIFT_FULL);
        assertThat(shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(shift.getId(),
                RegistrationStatus.APPROVED)).isZero();

        mockMvc.perform(patch("/api/v1/work-shifts/" + shift.getId() + "/registrations/review")
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"reason\":\"Đã đủ người\",\"items\":" + items + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].registrationStatus").value("REJECTED"));
    }

    @Test
    void myRegistrationsCarryTheShiftAndTheScheduleToWithdraw() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center);
        WorkShift shift = saveShift(center, today().plusDays(3), 8, 12, 2);
        approve(managerId, register(caregiver, shift).getId());
        register(caregiver, saveShift(center, today().plusDays(4), 8, 12, 2));

        mockMvc.perform(get("/api/v1/work-shifts/registrations/me").param("status", "APPROVED")
                        .with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].workDate").value(shift.getWorkDate().toString()))
                .andExpect(jsonPath("$.result.items[0].shiftType").value("MORNING"))
                .andExpect(jsonPath("$.result.items[0].shiftStatus").value("OPEN"))
                .andExpect(jsonPath("$.result.items[0].scheduleStatus").value("SCHEDULED"))
                .andExpect(jsonPath("$.result.items[0].scheduleId").isNotEmpty());
    }

    @Test
    void batchCreatesEveryDateOrNone() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        String url = "/api/v1/work-shifts/centers/" + center.getId() + "/batch";
        LocalDate first = today().plusDays(10);

        mockMvc.perform(post(url).with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchBody(List.of(first, first.plusDays(1), first.plusDays(2)), 120)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(3))
                .andExpect(jsonPath("$.result[0].registrationDeadline").value(vn(first, 6).toString()));
        expectCode(post(url), managerId, batchBody(List.of(first, first), null), ErrorCode.WORK_SHIFT_BATCH_INVALID);
        long before = workShiftRepository.count();
        expectCode(post(url), managerId, batchBody(List.of(today().minusDays(1), first.plusDays(5)), null),
                ErrorCode.WORK_SHIFT_DATE_INVALID);
        assertThat(workShiftRepository.count()).isEqualTo(before);
    }

    // ===== helpers =====

    void expectCode(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                    UUID managerId, String body, ErrorCode code) throws Exception {
        mockMvc.perform(request.with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(code.getStatusCode().value()))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    RequestPostProcessor user(UUID userId, String role) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    LocalDate today() {
        return LocalDate.now(ProviderTime.ZONE);
    }

    Instant vn(LocalDate day, int hour) {
        return ProviderTime.toInstant(day, LocalTime.of(hour, 0));
    }

    String shiftBody(LocalDate day, String start, String end, String type, String status, int capacity,
                     Instant deadline, String note) {
        return shiftBody(day, start, end, type, status, capacity, deadline, note, null);
    }

    String shiftBody(LocalDate day, String start, String end, String type, String status, int capacity,
                     Instant deadline, String note, Long version) {
        return "{\"workDate\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\","
                + "\"shiftType\":\"" + type + "\",\"shiftStatus\":\"" + status + "\",\"capacity\":" + capacity + ","
                + "\"registrationDeadline\":" + (deadline == null ? "null" : "\"" + deadline + "\"") + ","
                + "\"note\":" + (note == null ? "null" : "\"" + note + "\"") + ","
                + "\"version\":" + version + "}";
    }

    String cancelBody(String reason, long version) {
        return "{\"reason\":\"" + reason + "\",\"version\":" + version + "}";
    }

    String batchBody(List<LocalDate> dates, Integer leadMinutes) {
        List<String> quoted = dates.stream().map(date -> "\"" + date + "\"").toList();
        return "{\"workDates\":[" + String.join(",", quoted) + "],\"startTime\":\"08:00\",\"endTime\":\"12:00\","
                + "\"shiftType\":\"MORNING\",\"shiftStatus\":\"OPEN\",\"capacity\":2,"
                + "\"registrationLeadMinutes\":" + leadMinutes + "}";
    }

    CareCenter saveCenter(UUID managerId) {
        return careCenterRepository.save(CareCenter.builder()
                .centerName("Trung tâm " + managerId)
                .email(managerId + "@center.test")
                .address("1 Lê Lợi")
                .centerStatus(CenterStatus.ACTIVE)
                .managerUserId(managerId)
                .createdBy(managerId)
                .build());
    }

    CaregiverProfile saveCaregiver(CareCenter center) {
        UUID userId = UUID.randomUUID();
        return caregiverProfileRepository.save(CaregiverProfile.builder()
                .userId(userId)
                .center(center)
                .fullName("Caregiver " + userId)
                .experienceYears(2)
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.VERIFIED)
                .caregiverStatus(CaregiverStatus.ACTIVE)
                .createdBy(userId)
                .build());
    }

    WorkShift saveShift(CareCenter center, LocalDate day, int startHour, int endHour, int capacity) {
        return workShiftRepository.save(WorkShift.builder()
                .center(center)
                .workDate(day)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .shiftType("MORNING")
                .shiftStatus(ShiftStatus.OPEN)
                .capacity(capacity)
                .createdBy(center.getManagerUserId())
                .build());
    }

    ShiftRegistrationResponse register(CaregiverProfile caregiver, WorkShift shift) {
        return shiftRegistrationService.register(caregiver.getUserId(), shift.getId(),
                ShiftRegistrationRequest.builder().build()).getResult();
    }

    void approve(UUID managerId, UUID registrationId) {
        Long version = shiftRegistrationRepository.findById(registrationId).orElseThrow().getVersion();
        shiftRegistrationService.review(managerId, registrationId, RegistrationReviewRequest.builder()
                .status(RegistrationStatus.APPROVED)
                .version(version)
                .build());
    }
}
