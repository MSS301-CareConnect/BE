package mss301.providerservice;

import mss301.providerservice.client.dto.SystemCancellationRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.ProviderOutbox;
import mss301.providerservice.repository.ProviderOutboxRepository;
import mss301.providerservice.service.ProviderOutboxService;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.CaregiverSkill;
import mss301.providerservice.entity.CaregiverSkillId;
import mss301.providerservice.entity.CaregiverTimeOff;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.enums.CertificateVerificationStatus;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.repository.CaregiverScheduleRepository;
import mss301.providerservice.repository.CaregiverSkillRepository;
import mss301.providerservice.repository.CaregiverTimeOffRepository;
import mss301.providerservice.repository.CertificateRepository;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.util.ProviderTime;
import mss301.providerservice.service.ShiftRegistrationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import mss301.providerservice.client.CenterCascadeClient;
import mss301.providerservice.client.BookingArchiveClient;
import mss301.providerservice.client.dto.BookingCountResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.client.dto.CenterAccountsStatusRequest;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp trên H2 + repository thật: kỹ năng caregiver (PRV-01) với Catalog / Identity giả trên JDK HttpServer
 * (token SERVICE thật qua ServiceTokenProvider + Feign), và sửa lỗi ca làm việc (PRV-03) không mock count.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProviderIntegrationTests {

    static final String SERVICE_TOKEN = "provider-service-token";
    static final StubPlatform PLATFORM = new StubPlatform();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:provider_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("provider.identity.url", PLATFORM::baseUrl);
        registry.add("provider.care-catalog.url", PLATFORM::baseUrl);
        registry.add("provider.booking.url", PLATFORM::baseUrl);
        registry.add("provider.service-client.client-id", () -> "provider");
        registry.add("provider.service-client.client-secret", () -> "provider-secret");
    }

    @AfterAll
    static void stopPlatform() {
        PLATFORM.server.stop(0);
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CenterCascadeClient centerCascadeClient;

    @MockitoBean
    BookingArchiveClient bookingArchiveClient;

    @Autowired
    CareCenterRepository careCenterRepository;

    @Autowired
    CaregiverProfileRepository caregiverProfileRepository;

    @Autowired
    CaregiverSkillRepository caregiverSkillRepository;

    @Autowired
    WorkShiftRepository workShiftRepository;

    @Autowired
    ShiftRegistrationRepository shiftRegistrationRepository;

    @Autowired
    ShiftRegistrationService shiftRegistrationService;

    @Autowired
    CaregiverScheduleRepository caregiverScheduleRepository;

    @Autowired
    CaregiverTimeOffRepository caregiverTimeOffRepository;

    @Autowired
    CertificateRepository certificateRepository;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @AfterEach
    void resetPlatform() {
        PLATFORM.catalogFailing.set(false);
        PLATFORM.bookingFailing.set(false);
        PLATFORM.busyWindows.clear();
        PLATFORM.priceSummaries.clear();
        PLATFORM.bookingBodies.clear();
        PLATFORM.bookingAuthorizations.clear();
        PLATFORM.priceSummaryQueries.clear();
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===== PRV-01: kỹ năng caregiver =====

    @Test
    void managerAssignsReplacesAndReadsSkillsWithServiceToken() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        UUID feeding = PLATFORM.addOffering(center.getId(), "ACTIVE", "Cho ăn");
        UUID hygiene = PLATFORM.addOffering(center.getId(), "ACTIVE", "Vệ sinh");
        UUID medicine = PLATFORM.addOffering(center.getId(), "ACTIVE", "Uống thuốc");

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(feeding, hygiene, hygiene)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2));
        assertThat(PLATFORM.catalogAuthorizations).isNotEmpty().allMatch(("Bearer " + SERVICE_TOKEN)::equals);

        // Thay toàn bộ: bỏ "Cho ăn", giữ "Vệ sinh", thêm "Uống thuốc".
        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(hygiene, medicine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2));

        mockMvc.perform(get("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].centerServiceId").value(hygiene.toString()))
                .andExpect(jsonPath("$.result[0].serviceName").value("Vệ sinh"))
                .andExpect(jsonPath("$.result[0].careServiceId").value(PLATFORM.careServiceIdOf(hygiene).toString()))
                .andExpect(jsonPath("$.result[1].centerServiceId").value(medicine.toString()));

        // BookingService đọc chi tiết caregiver bằng token SERVICE (C5.5).
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId())
                        .with(user(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.centerServiceIds.length()").value(2))
                .andExpect(jsonPath("$.result.careServiceIds[0]").value(PLATFORM.careServiceIdOf(hygiene).toString()))
                .andExpect(jsonPath("$.result.careServiceIds[1]").value(PLATFORM.careServiceIdOf(medicine).toString()));
        assertThat(caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiver.getId())).hasSize(2);
    }

    @Test
    void caregiverWithoutSkillsReturnsEmptyLists() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());

        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId())
                        .with(user(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.careServiceIds").isArray())
                .andExpect(jsonPath("$.result.careServiceIds.length()").value(0))
                .andExpect(jsonPath("$.result.centerServiceIds.length()").value(0));
    }

    @Test
    void offeringOfAnotherCenterIs400() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CareCenter otherCenter = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        UUID otherOffering = PLATFORM.addOffering(otherCenter.getId(), "ACTIVE", "Cho ăn");

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherOffering)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SERVICE_NOT_IN_CENTER.getCode()));
        assertThat(caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiver.getId())).isEmpty();
    }

    @Test
    void notSellableOfferingIs409AndOtherManagerIs403() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        UUID inactive = PLATFORM.addOffering(center.getId(), "INACTIVE", "Cho ăn");

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(inactive)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SERVICE_NOT_ACTIVE.getCode()));

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_ACCESS_DENIED.getCode()));
    }

    @Test
    void catalogDownIs503AndKeepsExistingSkills() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        UUID feeding = PLATFORM.addOffering(center.getId(), "ACTIVE", "Cho ăn");
        UUID hygiene = PLATFORM.addOffering(center.getId(), "ACTIVE", "Vệ sinh");
        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(feeding)))
                .andExpect(status().isOk());
        PLATFORM.catalogFailing.set(true);

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(hygiene)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_UNAVAILABLE.getCode()));

        assertThat(caregiverSkillRepository.findByIdCaregiverIdOrderByAssignedAtAsc(caregiver.getId()))
                .singleElement()
                .satisfies(skill -> assertThat(skill.getId().getCenterServiceId()).isEqualTo(feeding));
    }

    @Test
    void tooManyServicesIs400AndCustomerCannotAssign() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        UUID[] ids = new UUID[51];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = UUID.randomUUID();
        }

        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_CAREGIVER_SERVICES.getCode()));
        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SERVICES_REQUIRED.getCode()));
        mockMvc.perform(put("/api/v1/caregivers/{id}/services", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());
    }

    // ===== PRV-03: ca làm việc =====

    @Test
    void capacityThreeClosesOnlyAtThirdApproval() {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        WorkShift shift = saveShift(center, LocalDate.now().plusDays(3), 3);
        List<UUID> registrationIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
            registrationIds.add(register(caregiver, shift).getId());
        }

        approve(managerId, registrationIds.get(0));
        approve(managerId, registrationIds.get(1));
        assertThat(workShiftRepository.findById(shift.getId()).orElseThrow().getShiftStatus()).isEqualTo(ShiftStatus.OPEN);

        approve(managerId, registrationIds.get(2));
        assertThat(workShiftRepository.findById(shift.getId()).orElseThrow().getShiftStatus())
                .isEqualTo(ShiftStatus.CLOSED);
        assertThat(shiftRegistrationRepository.countByWorkShiftIdAndRegistrationStatus(
                shift.getId(), RegistrationStatus.APPROVED)).isEqualTo(3);
    }

    @Test
    void caregiverCanRegisterAgainAfterCancelling() {
        CareCenter center = saveCenter(UUID.randomUUID());
        WorkShift shift = saveShift(center, LocalDate.now().plusDays(3), 2);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        ShiftRegistrationResponse first = register(caregiver, shift);
        assertThatThrownBy(() -> register(caregiver, shift))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.REGISTRATION_EXISTED);

        shiftRegistrationService.cancel(caregiver.getUserId(), first.getId(), first.getVersion());
        ShiftRegistrationResponse second = register(caregiver, shift);

        assertThat(second.getId()).isNotEqualTo(first.getId());
        ShiftRegistration cancelled = shiftRegistrationRepository.findById(first.getId()).orElseThrow();
        assertThat(cancelled.getRegistrationStatus()).isEqualTo(RegistrationStatus.CANCELLED);
    }

    @Test
    void startedShiftAndInactiveCenterBlockRegistration() {
        CareCenter center = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        WorkShift started = saveShift(center, LocalDate.now().minusDays(1), 2);
        assertThatThrownBy(() -> register(caregiver, started))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.WORK_SHIFT_ALREADY_STARTED);

        WorkShift future = saveShift(center, LocalDate.now().plusDays(3), 2);
        center.setCenterStatus(CenterStatus.SUSPENDED);
        careCenterRepository.save(center);
        assertThatThrownBy(() -> register(caregiver, future))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(ErrorCode.CENTER_NOT_ACTIVE);
    }

    @Test
    void workShiftListIsScopedByRole() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CareCenter otherCenter = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        saveShift(center, LocalDate.now().plusDays(3), 2);
        WorkShift draft = saveShift(center, LocalDate.now().plusDays(4), 2);
        draft.setShiftStatus(ShiftStatus.DRAFT);
        workShiftRepository.save(draft);
        saveShift(otherCenter, LocalDate.now().plusDays(3), 2);

        mockMvc.perform(get("/api/v1/work-shifts").with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/work-shifts").with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].approvedCount").value(0));
        mockMvc.perform(get("/api/v1/work-shifts").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mockMvc.perform(get("/api/v1/work-shifts")
                        .param("centerId", otherCenter.getId().toString())
                        .with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/work-shifts")
                        .param("centerId", otherCenter.getId().toString())
                        .with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    // ===== PRV-02: lịch rảnh + ngày nghỉ =====

    @Test
    void availabilityCheckMergesAdjacentShiftsAndReportsTimeOff() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        LocalDate day = futureDay();
        saveSchedule(caregiver, day, 8, 12);
        saveSchedule(caregiver, day, 12, 16);

        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(caregiver.getId(), slot(day, 10, 14))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(true))
                .andExpect(jsonPath("$.result.violations.length()").value(0));

        // Manager khai báo nghỉ 13–15 (manager không bị chặn bởi buổi đã giao → không gọi Booking).
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 13), vn(day, 15), "Khám bệnh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(caregiver.getId().toString()))
                .andExpect(jsonPath("$.result.createdBy").value(managerId.toString()));
        assertThat(PLATFORM.bookingBodies).isEmpty();

        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(caregiver.getId(), slot(day, 10, 14), slot(day, 8, 10), slot(day, 16, 18))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.available").value(false))
                .andExpect(jsonPath("$.result.violations.length()").value(2))
                .andExpect(jsonPath("$.result.violations[0].reason").value("TIME_OFF"))
                .andExpect(jsonPath("$.result.violations[0].startAt").value(vn(day, 10).toString()))
                .andExpect(jsonPath("$.result.violations[1].reason").value("NO_SCHEDULE"));

        // Khung rảnh tổng hợp cho khách: 08–13 và 15–16 (giờ VN).
        mockMvc.perform(get("/api/v1/caregivers/{id}/availability", caregiver.getId())
                        .param("from", vn(day, 0).toString())
                        .param("to", vn(day.plusDays(1), 0).toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].startAt").value(vn(day, 8).toString()))
                .andExpect(jsonPath("$.result[0].endAt").value(vn(day, 13).toString()))
                .andExpect(jsonPath("$.result[1].startAt").value(vn(day, 15).toString()))
                .andExpect(jsonPath("$.result[1].endAt").value(vn(day, 16).toString()));
        mockMvc.perform(get("/api/v1/caregivers/{id}/availability", caregiver.getId())
                        .param("from", vn(day, 0).toString())
                        .param("to", vn(day.plusDays(32), 0).toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.AVAILABILITY_RANGE_TOO_LONG.getCode()));
    }

    @Test
    void availabilityCheckIsServiceOnlyAndValidatesSlots() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        LocalDate day = futureDay();

        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(caregiver.getId(), slot(day, 10, 14))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(caregiver.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLOTS_INVALID.getCode()));
        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(caregiver.getId(), slot(day, 14, 10))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLOT_RANGE_INVALID.getCode()));
        mockMvc.perform(post("/api/v1/caregivers/availability-checks")
                        .with(user(UUID.randomUUID(), "SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkBody(UUID.randomUUID(), slot(day, 10, 14))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_FOUND.getCode()));
    }

    @Test
    void caregiverTimeOffOverlappingAssignedBookingIs409() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        LocalDate day = futureDay();
        PLATFORM.busyWindows.put(caregiver.getId(), List.<Instant[]>of(new Instant[]{vn(day, 9), vn(day, 11)}));

        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 0), vn(day.plusDays(1), 0), "Việc gia đình")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TIME_OFF_HAS_BOOKINGS.getCode()));
        assertThat(PLATFORM.bookingAuthorizations).isNotEmpty().allMatch(("Bearer " + SERVICE_TOKEN)::equals);
        assertThat(PLATFORM.bookingBodies.getLast()).contains(caregiver.getId().toString());

        // Không trùng buổi đã giao → tạo được; trùng ngày nghỉ khác → 409; quá 30 ngày → 400.
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 13), vn(day, 17), null)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 16), vn(day, 18), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TIME_OFF_OVERLAP.getCode()));
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day.plusDays(3), 0), vn(day.plusDays(34), 0), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TIME_OFF_RANGE_INVALID.getCode()));
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startAt\":\"" + vn(day, 18) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TIME_OFF_TIME_REQUIRED.getCode()));

        // Manager được tạo dù trùng buổi đã giao (phải tự đổi người cho buổi bị ảnh hưởng).
        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 9), vn(day, 11), "Manager xếp nghỉ")))
                .andExpect(status().isOk());

        // Người ngoài: caregiver khác / manager trung tâm khác không xem / tạo được.
        mockMvc.perform(get("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CENTER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());

        List<CaregiverTimeOff> saved = caregiverTimeOffRepository.findOverlapping(caregiver.getId(),
                vn(day, 0), vn(day.plusDays(1), 0));
        assertThat(saved).hasSize(2);
        UUID firstId = saved.getFirst().getId();
        mockMvc.perform(delete("/api/v1/caregivers/{id}/time-offs/{timeOffId}", caregiver.getId(), firstId)
                        .with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/caregivers/{id}/time-offs/{timeOffId}", caregiver.getId(), firstId)
                        .with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TIME_OFF_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .param("from", vn(day, 0).toString())
                        .param("to", vn(day.plusDays(1), 0).toString())
                        .with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
    }

    @Test
    void bookingDownIs503WhenCaregiverCreatesTimeOff() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        LocalDate day = futureDay();
        PLATFORM.bookingFailing.set(true);

        mockMvc.perform(post("/api/v1/caregivers/{id}/time-offs", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeOffBody(vn(day, 8), vn(day, 12), null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_SERVICE_UNAVAILABLE.getCode()));
        assertThat(caregiverTimeOffRepository.findOverlapping(caregiver.getId(), vn(day, 0), vn(day, 23))).isEmpty();
    }

    @Test
    void scheduleCanOnlyBeCancelledAndNotWhenBookingsAreAssigned() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        LocalDate day = futureDay();
        CaregiverSchedule busySchedule = saveSchedule(caregiver, day, 8, 12);
        CaregiverSchedule freeSchedule = saveSchedule(caregiver, day.plusDays(1), 8, 12);
        PLATFORM.busyWindows.put(caregiver.getId(), List.<Instant[]>of(new Instant[]{vn(day, 9), vn(day, 10)}));

        mockMvc.perform(patch("/api/v1/caregivers/schedules/{id}/status", busySchedule.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleStatusBody("IN_PROGRESS", null, busySchedule.getVersion())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/caregivers/schedules/{id}/status", busySchedule.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleStatusBody("CANCELLED", "Đổi lịch", busySchedule.getVersion())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SCHEDULE_HAS_BOOKINGS.getCode()));
        mockMvc.perform(patch("/api/v1/caregivers/schedules/{id}/status", freeSchedule.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleStatusBody("CANCELLED", "Đổi lịch", freeSchedule.getVersion())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.scheduleStatus").value("CANCELLED"));
        assertThat(caregiverScheduleRepository.findById(busySchedule.getId()).orElseThrow().getScheduleStatus())
                .isEqualTo(ScheduleStatus.SCHEDULED);

        // Endpoint trùng PATCH /care-centers/schedules/{id}/status đã bỏ.
        mockMvc.perform(patch("/api/v1/care-centers/schedules/{id}/status", freeSchedule.getId())
                        .with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleStatusBody("CANCELLED", "x", 0L)))
                .andExpect(status().isNotFound());
    }

    // ===== PRV-03 bổ sung: GET /work-shifts/{id} cùng phạm vi với danh sách =====

    @Test
    void workShiftDetailIsScopedLikeList() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CareCenter otherCenter = saveCenter(UUID.randomUUID());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        WorkShift open = saveShift(center, LocalDate.now().plusDays(3), 2);
        WorkShift draft = saveShift(center, LocalDate.now().plusDays(4), 2);
        draft.setShiftStatus(ShiftStatus.DRAFT);
        workShiftRepository.save(draft);
        WorkShift foreign = saveShift(otherCenter, LocalDate.now().plusDays(3), 2);

        mockMvc.perform(get("/api/v1/work-shifts/{id}", open.getId()).with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/work-shifts/{id}", draft.getId()).with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.WORK_SHIFT_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/work-shifts/{id}", foreign.getId()).with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/work-shifts/{id}", draft.getId()).with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/work-shifts/{id}", foreign.getId()).with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/work-shifts/{id}", foreign.getId()).with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/work-shifts/{id}", open.getId()).with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());
    }


    // ===== SRCH-01: tìm caregiver + hồ sơ công khai =====

    @Test
    void searchReturnsOnlyCaregiversWithAllRequestedServices() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        UUID feeding = UUID.randomUUID();
        UUID hygiene = UUID.randomUUID();
        CaregiverProfile both = saveCaregiver(center, UUID.randomUUID());
        addSkills(both, feeding, hygiene);
        CaregiverProfile feedingOnly = saveCaregiver(center, UUID.randomUUID());
        addSkills(feedingOnly, feeding);
        CaregiverProfile pending = saveCaregiver(center, UUID.randomUUID());
        pending.setVerificationStatus(VerificationStatus.PENDING);
        caregiverProfileRepository.save(pending);
        addSkills(pending, feeding, hygiene);

        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString(), hygiene.toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(both.getId().toString()))
                .andExpect(jsonPath("$.result.items[0].careServiceIds.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].verifiedCertificateCount").value(0))
                .andExpect(jsonPath("$.result.items[0].userId").doesNotExist());
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        assertThat(PLATFORM.bookingBodies).isEmpty();
    }

    @Test
    void searchFiltersRatingAndSorts() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        UUID feeding = UUID.randomUUID();
        CaregiverProfile topRated = saveRatedCaregiver(center, "Bình", "4.80", 1);
        CaregiverProfile experienced = saveRatedCaregiver(center, "An", "4.00", 9);
        CaregiverProfile lowRated = saveRatedCaregiver(center, "Cúc", "3.50", 5);
        addSkills(topRated, feeding);
        addSkills(experienced, feeding);
        addSkills(lowRated, feeding);

        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("minRating", "4")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(topRated.getId().toString()))
                .andExpect(jsonPath("$.result.items[1].caregiverId").value(experienced.getId().toString()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("sort", "EXPERIENCE_DESC")
                        .param("size", "2")
                        .param("page", "0")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(experienced.getId().toString()))
                .andExpect(jsonPath("$.result.items[1].caregiverId").value(lowRated.getId().toString()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("sort", "NAME_ASC")
                        .param("keyword", "n")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.items[0].fullName").value("An"));
    }

    @Test
    void searchWithSlotsExcludesBusyOffAndUnscheduledCaregivers() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        UUID feeding = UUID.randomUUID();
        LocalDate day = futureDay();
        CaregiverProfile busy = saveCaregiver(center, UUID.randomUUID());
        CaregiverProfile free = saveCaregiver(center, UUID.randomUUID());
        CaregiverProfile onLeave = saveCaregiver(center, UUID.randomUUID());
        CaregiverProfile unscheduled = saveCaregiver(center, UUID.randomUUID());
        for (CaregiverProfile caregiver : List.of(busy, free, onLeave, unscheduled)) {
            addSkills(caregiver, feeding);
        }
        for (CaregiverProfile caregiver : List.of(busy, free, onLeave)) {
            saveSchedule(caregiver, day, 8, 16);
        }
        caregiverTimeOffRepository.save(CaregiverTimeOff.builder()
                .caregiver(onLeave)
                .startAt(vn(day, 0))
                .endAt(vn(day.plusDays(1), 0))
                .createdBy(onLeave.getUserId())
                .build());
        PLATFORM.busyWindows.put(busy.getId(), List.<Instant[]>of(new Instant[]{vn(day, 9), vn(day, 10)}));

        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("slotStart", vn(day, 9).toString())
                        .param("slotEnd", vn(day, 11).toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(free.getId().toString()));
        // Lịch rảnh / ngày nghỉ lọc ở Provider TRƯỚC: Booking chỉ được hỏi về caregiver còn lại, bằng token SERVICE.
        assertThat(PLATFORM.bookingBodies).singleElement().satisfies(body -> assertThat(body)
                .contains(busy.getId().toString(), free.getId().toString())
                .doesNotContain(onLeave.getId().toString(), unscheduled.getId().toString()));
        assertThat(PLATFORM.bookingAuthorizations).allMatch(("Bearer " + SERVICE_TOKEN)::equals);

        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("slots", vn(day, 13) + "/" + vn(day, 15), vn(day, 8) + "/" + vn(day, 9))
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("slots", "not-a-slot")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLOT_RANGE_INVALID.getCode()));

        PLATFORM.bookingFailing.set(true);
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("slotStart", vn(day, 9).toString())
                        .param("slotEnd", vn(day, 11).toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_SERVICE_UNAVAILABLE.getCode()));
    }

    @Test
    void searchValidatesInputAndRejectsTooBroadSearch() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        UUID feeding = UUID.randomUUID();
        String[] elevenIds = new String[11];
        for (int i = 0; i < elevenIds.length; i++) {
            elevenIds[i] = UUID.randomUUID().toString();
        }

        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("careServiceIds", feeding.toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEARCH_CENTER_REQUIRED.getCode()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_SERVICE_IDS_REQUIRED.getCode()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", elevenIds)
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_SERVICE_IDS_REQUIRED.getCode()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("minRating", "6")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.MIN_RATING_INVALID.getCode()));
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .param("size", "51")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGE.getCode()));

        CareCenter suspended = saveCenter(UUID.randomUUID());
        suspended.setCenterStatus(CenterStatus.SUSPENDED);
        careCenterRepository.save(suspended);
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", suspended.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_NOT_ACTIVE.getCode()));

        List<CaregiverProfile> crowd = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            crowd.add(newCaregiver(center, UUID.randomUUID()));
        }
        List<CaregiverSkill> crowdSkills = new ArrayList<>();
        for (CaregiverProfile caregiver : caregiverProfileRepository.saveAll(crowd)) {
            crowdSkills.add(newSkill(caregiver, feeding));
        }
        caregiverSkillRepository.saveAll(crowdSkills);
        mockMvc.perform(get("/api/v1/caregivers/search")
                        .param("centerId", center.getId().toString())
                        .param("careServiceIds", feeding.toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SEARCH_TOO_BROAD.getCode()));
    }

    @Test
    void publicProfileShowsOnlyAdminVerifiedCertificatesAndFullProfileIsRestricted() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        addSkills(caregiver, UUID.randomUUID());
        saveCertificate(caregiver, "Điều dưỡng", VerificationStatus.VERIFIED);
        saveCertificate(caregiver, "Sơ cứu", VerificationStatus.PENDING);
        saveCertificate(caregiver, "Giả", VerificationStatus.REJECTED);
        CaregiverProfile pending = saveCaregiver(center, UUID.randomUUID());
        pending.setVerificationStatus(VerificationStatus.PENDING);
        caregiverProfileRepository.save(pending);

        mockMvc.perform(get("/api/v1/caregivers/{id}/public", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(caregiver.getId().toString()))
                .andExpect(jsonPath("$.result.centerName").value(center.getCenterName()))
                .andExpect(jsonPath("$.result.certificates.length()").value(1))
                .andExpect(jsonPath("$.result.certificates[0].certificateName").value("Điều dưỡng"))
                .andExpect(jsonPath("$.result.certificates[0].credentialNumber").doesNotExist())
                .andExpect(jsonPath("$.result.services.length()").value(1))
                .andExpect(jsonPath("$.result.services[0].serviceName").value("Dịch vụ"))
                .andExpect(jsonPath("$.result.userId").doesNotExist())
                .andExpect(jsonPath("$.result.verificationStatus").doesNotExist())
                .andExpect(jsonPath("$.result.version").doesNotExist());
        mockMvc.perform(get("/api/v1/caregivers/{id}/public", pending.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound());

        // Danh mục cho khách chỉ trả thẻ công khai, không có field nội bộ (M-02).
        mockMvc.perform(get("/api/v1/caregivers").param("centerId", center.getId().toString())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(caregiver.getId().toString()))
                .andExpect(jsonPath("$.result.items[0].centerName").value(center.getCenterName()))
                .andExpect(jsonPath("$.result.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].verificationReason").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].createdBy").doesNotExist());

        // GET /caregivers/{id} (hồ sơ đầy đủ): ADMIN / manager trung tâm / chính caregiver / SERVICE.
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId()).with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId())
                        .with(user(UUID.randomUUID(), "CENTER_MANAGER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId())
                        .with(user(pending.getUserId(), "CAREGIVER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId())
                        .with(user(caregiver.getUserId(), "CAREGIVER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId()).with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/caregivers/{id}", caregiver.getId()).with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk());
    }

    // ===== SRCH-02: trung tâm ACTIVE cho khách =====

    @Test
    void publicCentersShowOnlyActiveCentersWithoutInternalFields() throws Exception {
        String tag = UUID.randomUUID().toString();
        CareCenter active = saveNamedCenter("Public " + tag + " A", CenterStatus.ACTIVE);
        CareCenter suspended = saveNamedCenter("Public " + tag + " B", CenterStatus.SUSPENDED);
        PLATFORM.priceSummaries.put(active.getId(), 120000L);

        mockMvc.perform(get("/api/v1/care-centers/public")
                        .param("keyword", tag)
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].centerId").value(active.getId().toString()))
                .andExpect(jsonPath("$.result.items[0].minPrice").value(120000))
                .andExpect(jsonPath("$.result.items[0].imageUrls.length()").value(0))
                .andExpect(jsonPath("$.result.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].managerUserId").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].createdBy").doesNotExist());
        mockMvc.perform(get("/api/v1/care-centers/public/{id}", suspended.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/care-centers/public/{id}", active.getId())
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.centerName").value(active.getCenterName()))
                .andExpect(jsonPath("$.result.email").doesNotExist());
        mockMvc.perform(get("/api/v1/care-centers/public").param("keyword", tag))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicCentersFilterByServiceAndPriceThroughCatalog() throws Exception {
        String tag = UUID.randomUUID().toString();
        UUID careServiceId = UUID.randomUUID();
        CareCenter cheap = saveNamedCenter("Price " + tag + " cheap", CenterStatus.ACTIVE);
        CareCenter expensive = saveNamedCenter("Price " + tag + " expensive", CenterStatus.ACTIVE);
        saveNamedCenter("Price " + tag + " no service", CenterStatus.ACTIVE);
        PLATFORM.priceSummaries.put(cheap.getId(), 120000L);
        PLATFORM.priceSummaries.put(expensive.getId(), 200000L);

        mockMvc.perform(get("/api/v1/care-centers/public")
                        .param("keyword", tag)
                        .param("careServiceId", careServiceId.toString())
                        .param("maxPrice", "150000")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].centerId").value(cheap.getId().toString()));
        assertThat(PLATFORM.priceSummaryQueries.getLast())
                .contains("careServiceId=" + careServiceId)
                .contains("maxPrice=150000");
        assertThat(PLATFORM.catalogAuthorizations.getLast()).isEqualTo("Bearer " + SERVICE_TOKEN);

        mockMvc.perform(get("/api/v1/care-centers/public")
                        .param("minPrice", "200000")
                        .param("maxPrice", "100000")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRICE_RANGE_INVALID.getCode()));

        PLATFORM.catalogFailing.set(true);
        mockMvc.perform(get("/api/v1/care-centers/public")
                        .param("keyword", tag)
                        .param("maxPrice", "150000")
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_UNAVAILABLE.getCode()));
        // Không lọc giá: Catalog lỗi chỉ làm mất minPrice, danh sách vẫn trả.
        mockMvc.perform(get("/api/v1/care-centers/public")
                        .param("keyword", tag)
                        .with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.items[0].minPrice").doesNotExist());
    }


    @Test
    void managerCenterListAndCaregiverStatusAreScoped() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        saveCenter(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/care-centers").with(user(managerId, "CENTER_MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(center.getId().toString()));
        mockMvc.perform(get("/api/v1/care-centers/" + center.getId()).with(user(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.managerUserId").doesNotExist());
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        caregiver.setVerificationStatus(VerificationStatus.PENDING);
        caregiver.setCaregiverStatus(CaregiverStatus.INACTIVE);
        caregiver = caregiverProfileRepository.saveAndFlush(caregiver);
        mockMvc.perform(patch("/api/v1/caregivers/" + caregiver.getId() + "/status").with(user(managerId, "CENTER_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"version\":" + caregiver.getVersion() + "}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_VERIFIED.getCode()));
    }

    @Test
    void certificatesRequireManagerThenAdminAndRejectExpired() throws Exception {
        UUID managerId = UUID.randomUUID();
        CaregiverProfile caregiver = saveCaregiver(saveCenter(managerId), UUID.randomUUID());
        Certificate certificate = certificateRepository.saveAndFlush(Certificate.builder().caregiver(caregiver)
                .certificateName("Chứng chỉ").issuedBy("Cơ sở đào tạo").verificationStatus(CertificateVerificationStatus.PENDING)
                .expiryDate(LocalDate.now(ProviderTime.ZONE).plusDays(30)).build());
        String path = "/api/v1/certificates/" + certificate.getId();
        mockMvc.perform(patch(path + "/verification").with(user(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"version\":0}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch(path + "/center-verification").with(user(UUID.randomUUID(), "CENTER_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"version\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch(path + "/center-verification").with(user(managerId, "CENTER_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.verificationStatus").value("CENTER_VERIFIED"));
        mockMvc.perform(patch(path + "/verification").with(user(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"version\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.verificationStatus").value("ADMIN_VERIFIED"));
        Certificate expired = certificateRepository.saveAndFlush(Certificate.builder().caregiver(caregiver).certificateName("Hết hạn")
                .issuedBy("Cơ sở").verificationStatus(CertificateVerificationStatus.PENDING)
                .expiryDate(LocalDate.now(ProviderTime.ZONE).minusDays(1)).build());
        mockMvc.perform(patch("/api/v1/certificates/" + expired.getId() + "/center-verification").with(user(managerId, "CENTER_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"version\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.CERTIFICATE_EXPIRED.getCode()));
    }

    @Test
    void centerSuspensionUsesOrderedCascadeAndArchiveChecksBookings() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        String path = "/api/v1/care-centers/" + center.getId();
        mockMvc.perform(patch(path + "/status").with(user(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"Kiểm tra\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch(path + "/status").with(user(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"version\":1}"))
                .andExpect(status().isOk());
        ArgumentCaptor<CenterAccountsStatusRequest> requests = ArgumentCaptor.forClass(CenterAccountsStatusRequest.class);
        verify(centerCascadeClient, times(2)).updateAccounts(eq(center.getId()), requests.capture());
        assertThat(requests.getAllValues()).extracting(CenterAccountsStatusRequest::getStatus).containsExactly("LOCKED", "ACTIVE");
        verify(centerCascadeClient).cancelBookings(any());
        when(bookingArchiveClient.list(eq(center.getId()), any(), anyInt(), anyInt())).thenReturn(
                RemoteApiResponse.<BookingCountResponse>builder().code(200).result(BookingCountResponse.builder().totalElements(1L).build()).build());
        mockMvc.perform(delete(path).param("version", "2").with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.CENTER_HAS_ACTIVE_BOOKINGS.getCode()));
        // M-06: ngưng hoạt động cũng bị chặn khi còn booking chưa kết thúc.
        mockMvc.perform(patch(path + "/status").with(user(UUID.randomUUID(), "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"reason\":\"Tạm nghỉ\",\"version\":2}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.CENTER_HAS_ACTIVE_BOOKINGS.getCode()));
        assertThat(careCenterRepository.findById(center.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Autowired
    ProviderOutboxRepository providerOutboxRepository;

    @Autowired
    ProviderOutboxService providerOutboxService;

    @Test
    void permanentCascadeFailureStopsRetryImmediately() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        doThrow(new AppException(ErrorCode.REMOTE_COMMAND_REJECTED))
                .when(centerCascadeClient).updateAccounts(eq(center.getId()), any());
        mockMvc.perform(patch("/api/v1/care-centers/" + center.getId() + "/status")
                        .with(user(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Kiểm tra\",\"version\":0}"))
                .andExpect(status().isOk());
        ProviderOutbox outbox = providerOutboxRepository.findAll().stream()
                .filter(item -> item.getCenterId().equals(center.getId())).findFirst().orElseThrow();
        assertThat(outbox.getStatus()).isEqualTo("FAILED");
        assertThat(outbox.getAttempts()).isEqualTo(1);
        providerOutboxService.dispatch(outbox.getId());
        verify(centerCascadeClient, times(1)).updateAccounts(eq(center.getId()), any());
    }

    // H-08: sự kiện FAILED chặn sự kiện sau của cùng trung tâm cho tới khi admin xếp lại hoặc bỏ qua.
    @Test
    void adminRequeuesOrSkipsFailedCascadeAndUnblocksLaterEvents() throws Exception {
        CareCenter center = saveCenter(UUID.randomUUID());
        doThrow(new AppException(ErrorCode.REMOTE_COMMAND_REJECTED))
                .when(centerCascadeClient).updateAccounts(eq(center.getId()), any());
        mockMvc.perform(patch("/api/v1/care-centers/" + center.getId() + "/status")
                        .with(user(UUID.randomUUID(), "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Kiểm tra\",\"version\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/care-centers/" + center.getId() + "/status")
                        .with(user(UUID.randomUUID(), "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"Mở lại\",\"version\":1}"))
                .andExpect(status().isOk());
        List<ProviderOutbox> rows = providerOutboxRepository.findAll().stream()
                .filter(item -> item.getCenterId().equals(center.getId()))
                .sorted(java.util.Comparator.comparing(ProviderOutbox::getSequenceNo)).toList();
        ProviderOutbox suspend = rows.getFirst();
        ProviderOutbox resume = rows.getLast();
        assertThat(suspend.getStatus()).isEqualTo("FAILED");
        // RESUME bị chặn sau SUSPEND FAILED.
        assertThat(resume.getStatus()).isEqualTo("PENDING");
        assertThat(resume.getAttempts()).isZero();

        mockMvc.perform(get("/api/v1/provider-outbox").param("status", "FAILED").with(user(UUID.randomUUID(), "CENTER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/provider-outbox").param("status", "FAILED").param("size", "100")
                        .with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[?(@.id == '" + suspend.getId() + "')]").isNotEmpty());
        mockMvc.perform(post("/api/v1/provider-outbox/" + resume.getId() + "/requeue").with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.OUTBOX_NOT_FAILED.getCode()));

        // Admin khóa tài khoản tay rồi bỏ qua SUSPEND → RESUME được gửi.
        mockMvc.perform(post("/api/v1/provider-outbox/" + suspend.getId() + "/skip").with(user(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Đã khóa tay\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("SKIPPED"));
        doNothing().when(centerCascadeClient).updateAccounts(eq(center.getId()), any());
        providerOutboxService.dispatch(resume.getId());
        assertThat(providerOutboxRepository.findById(resume.getId()).orElseThrow().getStatus()).isEqualTo("SENT");

        // Xếp lại: FAILED → PENDING, đếm lại số lần thử.
        ProviderOutbox failed = providerOutboxRepository.findById(suspend.getId()).orElseThrow();
        failed.setStatus("FAILED");
        providerOutboxRepository.save(failed);
        mockMvc.perform(post("/api/v1/provider-outbox/" + suspend.getId() + "/requeue").with(user(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.attempts").value(0));
    }

    // H-11: caregiver ACTIVE bị tạm khóa → Booking gắn cờ đổi người (system-cancellations CAREGIVER_DEACTIVATED) sau commit.
    @Test
    void suspendingAnActiveCaregiverFlagsTheirUpcomingVisitsInBooking() throws Exception {
        UUID managerId = UUID.randomUUID();
        CareCenter center = saveCenter(managerId);
        CaregiverProfile caregiver = saveCaregiver(center, UUID.randomUUID());
        mockMvc.perform(patch("/api/v1/caregivers/" + caregiver.getId() + "/status").with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"version\":" + caregiver.getVersion() + "}"))
                .andExpect(status().isOk());
        ArgumentCaptor<SystemCancellationRequest> requests = ArgumentCaptor.forClass(SystemCancellationRequest.class);
        verify(centerCascadeClient).cancelBookings(requests.capture());
        assertThat(requests.getValue().getCaregiverId()).isEqualTo(caregiver.getId());
        assertThat(requests.getValue().getCenterId()).isNull();
        assertThat(requests.getValue().getReason()).isEqualTo("CAREGIVER_DEACTIVATED");
        ProviderOutbox outbox = providerOutboxRepository.findAll().stream()
                .filter(item -> item.getCenterId().equals(center.getId())).findFirst().orElseThrow();
        assertThat(outbox.getEventType()).isEqualTo("CAREGIVER_DEACTIVATED");
        assertThat(outbox.getStatus()).isEqualTo("SENT");

        // SUSPENDED → INACTIVE: không còn ACTIVE trước đó → không cascade lần hai.
        CaregiverProfile suspended = caregiverProfileRepository.findById(caregiver.getId()).orElseThrow();
        mockMvc.perform(patch("/api/v1/caregivers/" + caregiver.getId() + "/status").with(user(managerId, "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\",\"version\":" + suspended.getVersion() + "}"))
                .andExpect(status().isOk());
        verify(centerCascadeClient, times(1)).cancelBookings(any());
    }

    // ===== helpers =====

    RequestPostProcessor user(UUID userId, String role) {
        return jwt().jwt(token -> token.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    String body(UUID... centerServiceIds) {
        List<String> quoted = new ArrayList<>();
        for (UUID id : centerServiceIds) {
            quoted.add("\"" + id + "\"");
        }
        return "{\"centerServiceIds\":[" + String.join(",", quoted) + "]}";
    }

    CareCenter saveCenter(UUID managerId) {
        CareCenter center = CareCenter.builder()
                .centerName("Trung tâm " + managerId)
                .email(managerId + "@center.test")
                .address("1 Lê Lợi")
                .centerStatus(CenterStatus.ACTIVE)
                .managerUserId(managerId)
                .createdBy(managerId)
                .build();
        return careCenterRepository.save(center);
    }

    CaregiverProfile saveCaregiver(CareCenter center, UUID userId) {
        CaregiverProfile caregiver = CaregiverProfile.builder()
                .userId(userId)
                .center(center)
                .fullName("Caregiver " + userId)
                .experienceYears(2)
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.VERIFIED)
                .caregiverStatus(CaregiverStatus.ACTIVE)
                .createdBy(userId)
                .build();
        return caregiverProfileRepository.save(caregiver);
    }

    WorkShift saveShift(CareCenter center, LocalDate workDate, int capacity) {
        WorkShift shift = WorkShift.builder()
                .center(center)
                .workDate(workDate)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .shiftType("MORNING")
                .shiftStatus(ShiftStatus.OPEN)
                .capacity(capacity)
                .createdBy(center.getManagerUserId())
                .build();
        return workShiftRepository.save(shift);
    }

    ShiftRegistrationResponse register(CaregiverProfile caregiver, WorkShift shift) {
        return shiftRegistrationService.register(caregiver.getUserId(), shift.getId(),
                ShiftRegistrationRequest.builder().build()).getResult();
    }

    void approve(UUID managerId, UUID registrationId) {
        Long version = shiftRegistrationRepository.findById(registrationId).orElseThrow().getVersion();
        RegistrationReviewRequest request = RegistrationReviewRequest.builder()
                .status(RegistrationStatus.APPROVED)
                .version(version)
                .build();
        shiftRegistrationService.review(managerId, registrationId, request);
    }

    CaregiverProfile newCaregiver(CareCenter center, UUID userId) {
        return CaregiverProfile.builder()
                .userId(userId)
                .center(center)
                .fullName("Caregiver " + userId)
                .experienceYears(2)
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.VERIFIED)
                .caregiverStatus(CaregiverStatus.ACTIVE)
                .createdBy(userId)
                .build();
    }

    CaregiverProfile saveRatedCaregiver(CareCenter center, String fullName, String rating, int experienceYears) {
        CaregiverProfile caregiver = newCaregiver(center, UUID.randomUUID());
        caregiver.setFullName(fullName);
        caregiver.setRatingAverage(new BigDecimal(rating));
        caregiver.setRatingCount(10);
        caregiver.setExperienceYears(experienceYears);
        return caregiverProfileRepository.save(caregiver);
    }

    CaregiverSkill newSkill(CaregiverProfile caregiver, UUID careServiceId) {
        return CaregiverSkill.builder()
                .id(new CaregiverSkillId(caregiver.getId(), UUID.randomUUID()))
                .caregiver(caregiver)
                .careServiceId(careServiceId)
                .serviceName("Dịch vụ")
                .assignedBy(UUID.randomUUID())
                .assignedAt(Instant.now())
                .build();
    }

    void addSkills(CaregiverProfile caregiver, UUID... careServiceIds) {
        List<CaregiverSkill> skills = new ArrayList<>();
        for (UUID careServiceId : careServiceIds) {
            skills.add(newSkill(caregiver, careServiceId));
        }
        caregiverSkillRepository.saveAll(skills);
    }

    void saveCertificate(CaregiverProfile caregiver, String name, VerificationStatus status) {
        certificateRepository.save(Certificate.builder()
                .caregiver(caregiver)
                .certificateName(name)
                .issuedBy("Bộ Y tế")
                .credentialNumber("SECRET-" + name)
                .verificationStatus(status == VerificationStatus.VERIFIED ? CertificateVerificationStatus.ADMIN_VERIFIED
                        : CertificateVerificationStatus.valueOf(status.name()))
                .build());
    }

    CareCenter saveNamedCenter(String name, CenterStatus status) {
        UUID managerId = UUID.randomUUID();
        CareCenter center = CareCenter.builder()
                .centerName(name)
                .email(managerId + "@center.test")
                .address("2 Hai Bà Trưng")
                .centerStatus(status)
                .managerUserId(managerId)
                .createdBy(managerId)
                .build();
        return careCenterRepository.save(center);
    }

    LocalDate futureDay() {
        return LocalDate.now(ProviderTime.ZONE).plusDays(5);
    }

    Instant vn(LocalDate day, int hour) {
        return ProviderTime.toInstant(day, LocalTime.of(hour, 0));
    }

    String slot(LocalDate day, int startHour, int endHour) {
        return "{\"startAt\":\"" + vn(day, startHour) + "\",\"endAt\":\"" + vn(day, endHour) + "\"}";
    }

    String checkBody(UUID caregiverId, String... slots) {
        return "{\"caregiverId\":\"" + caregiverId + "\",\"slots\":[" + String.join(",", slots) + "]}";
    }

    String timeOffBody(Instant startAt, Instant endAt, String reason) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return "{\"startAt\":\"" + startAt + "\",\"endAt\":\"" + endAt + "\",\"reason\":" + reasonJson + "}";
    }

    String scheduleStatusBody(String status, String reason, Long version) {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        return "{\"status\":\"" + status + "\",\"reason\":" + reasonJson + ",\"version\":" + version + "}";
    }

    // Lịch rảnh SCHEDULED (ca + đăng ký APPROVED) giờ VN, như sau khi manager duyệt đăng ký ca.
    CaregiverSchedule saveSchedule(CaregiverProfile caregiver, LocalDate day, int startHour, int endHour) {
        CareCenter center = caregiver.getCenter();
        WorkShift shift = workShiftRepository.save(WorkShift.builder()
                .center(center)
                .workDate(day)
                .startTime(LocalTime.of(startHour, 0))
                .endTime(LocalTime.of(endHour, 0))
                .shiftType("DAY")
                .shiftStatus(ShiftStatus.OPEN)
                .capacity(5)
                .createdBy(center.getManagerUserId())
                .build());
        ShiftRegistration registration = shiftRegistrationRepository.save(ShiftRegistration.builder()
                .workShift(shift)
                .caregiver(caregiver)
                .registrationStatus(RegistrationStatus.APPROVED)
                .build());
        return caregiverScheduleRepository.save(CaregiverSchedule.builder()
                .registration(registration)
                .caregiver(caregiver)
                .workShift(shift)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .assignedBy(center.getManagerUserId())
                .assignedAt(Instant.now())
                .build());
    }


    /** Identity (service-token) + Catalog (center-services) + Booking (busy-caregivers) giả qua "gateway" duy nhất. */
    static final class StubPlatform {
        final HttpServer server;
        final Map<UUID, String> offerings = new ConcurrentHashMap<>();
        final Map<UUID, UUID> careServiceIds = new ConcurrentHashMap<>();
        final List<String> catalogAuthorizations = new CopyOnWriteArrayList<>();
        final AtomicBoolean catalogFailing = new AtomicBoolean(false);
        final AtomicBoolean bookingFailing = new AtomicBoolean(false);
        final Map<UUID, List<Instant[]>> busyWindows = new ConcurrentHashMap<>();
        final List<String> bookingAuthorizations = new CopyOnWriteArrayList<>();
        final List<String> bookingBodies = new CopyOnWriteArrayList<>();
        final Map<UUID, Long> priceSummaries = new ConcurrentHashMap<>();
        final List<String> priceSummaryQueries = new CopyOnWriteArrayList<>();

        StubPlatform() {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new ExceptionInInitializerError(e);
            }
            server.createContext("/api/v1/auth/service-token", exchange -> respond(exchange, 200,
                    "{\"code\":200,\"message\":\"ok\",\"result\":{\"accessToken\":\"" + SERVICE_TOKEN
                            + "\",\"tokenType\":\"Bearer\",\"expiresInSeconds\":900}}"));
            server.createContext("/api/v1/center-services/", this::handleCenterService);
            server.createContext("/api/v1/booking-occurrences/busy-caregivers", this::handleBusyCaregivers);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        UUID addOffering(UUID centerId, String status, String name) {
            UUID id = UUID.randomUUID();
            UUID careServiceId = UUID.randomUUID();
            careServiceIds.put(id, careServiceId);
            offerings.put(id, "{\"id\":\"" + id + "\",\"catalogItem\":{\"id\":\"" + careServiceId + "\",\"name\":\""
                    + name + "\",\"category\":\"BASIC\",\"status\":\"ACTIVE\"},\"centerId\":\"" + centerId
                    + "\",\"description\":null,\"status\":\"" + status + "\",\"pricePlans\":[]}");
            return id;
        }

        UUID careServiceIdOf(UUID offeringId) {
            return careServiceIds.get(offeringId);
        }

        void handleCenterService(HttpExchange exchange) throws IOException {
            catalogAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (catalogFailing.get()) {
                respond(exchange, 500, "{\"code\":9999,\"message\":\"down\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/price-summary")) {
                handlePriceSummary(exchange);
                return;
            }
            UUID id = UUID.fromString(path.substring(path.lastIndexOf('/') + 1));
            String offering = offerings.get(id);
            if (offering == null) {
                respond(exchange, 404, "{\"code\":3200,\"message\":\"not found\"}");
                return;
            }
            respond(exchange, 200, "{\"code\":200,\"message\":\"ok\",\"result\":" + offering + ",\"extra\":1}");
        }

        // C9.2: lọc theo minPrice/maxPrice như Catalog thật; careServiceId chỉ ghi lại để test kiểm đã chuyển tiếp.
        void handlePriceSummary(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getRawQuery();
            priceSummaryQueries.add(query == null ? "" : URLDecoder.decode(query, StandardCharsets.UTF_8));
            Map<String, String> params = new HashMap<>();
            if (query != null) {
                for (String pair : query.split("&")) {
                    String[] parts = pair.split("=", 2);
                    params.put(parts[0], parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
                }
            }
            BigDecimal minPrice = params.containsKey("minPrice") ? new BigDecimal(params.get("minPrice")) : null;
            BigDecimal maxPrice = params.containsKey("maxPrice") ? new BigDecimal(params.get("maxPrice")) : null;
            List<String> items = new ArrayList<>();
            priceSummaries.forEach((centerId, price) -> {
                BigDecimal value = BigDecimal.valueOf(price);
                boolean aboveMin = minPrice == null || value.compareTo(minPrice) >= 0;
                boolean belowMax = maxPrice == null || value.compareTo(maxPrice) <= 0;
                if (aboveMin && belowMax) {
                    items.add("{\"centerId\":\"" + centerId + "\",\"minPrice\":" + price + "}");
                }
            });
            respond(exchange, 200, "{\"code\":200,\"result\":[" + String.join(",", items) + "]}");
        }

        // C5.1: caregiver bận khi có khung bận giao (nửa mở) bất kỳ slot nào trong request.
        void handleBusyCaregivers(HttpExchange exchange) throws IOException {
            bookingAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bookingBodies.add(body);
            if (bookingFailing.get()) {
                respond(exchange, 500, "{\"code\":9999,\"message\":\"down\"}");
                return;
            }
            String idsPart = body.substring(body.indexOf("\"caregiverIds\""), body.indexOf("]") + 1);
            List<UUID> caregiverIds = new ArrayList<>();
            Matcher idMatcher = Pattern.compile("[0-9a-f]{8}-[0-9a-f-]{27}").matcher(idsPart);
            while (idMatcher.find()) {
                caregiverIds.add(UUID.fromString(idMatcher.group()));
            }
            List<Instant[]> slots = new ArrayList<>();
            Matcher slotMatcher = Pattern.compile("\"startAt\":\"([^\"]+)\",\"endAt\":\"([^\"]+)\"").matcher(body);
            while (slotMatcher.find()) {
                slots.add(new Instant[]{Instant.parse(slotMatcher.group(1)), Instant.parse(slotMatcher.group(2))});
            }
            List<String> busy = new ArrayList<>();
            for (UUID caregiverId : caregiverIds) {
                boolean overlaps = busyWindows.getOrDefault(caregiverId, List.of()).stream()
                        .anyMatch(window -> slots.stream()
                                .anyMatch(slot -> slot[0].isBefore(window[1]) && slot[1].isAfter(window[0])));
                if (overlaps) {
                    busy.add("\"" + caregiverId + "\"");
                }
            }
            respond(exchange, 200, "{\"code\":200,\"message\":\"ok\",\"result\":{\"busyCaregiverIds\":["
                    + String.join(",", busy) + "]},\"extra\":1}");
        }

        void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
