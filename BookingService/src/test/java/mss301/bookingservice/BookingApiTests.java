package mss301.bookingservice;

import mss301.bookingservice.service.CareSessionService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.Cookie;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingStatusHistory;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.entity.CareTask;
import mss301.bookingservice.enums.CareTaskStatus;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingOutboxRepository;
import mss301.bookingservice.repository.CareTaskRepository;
import mss301.bookingservice.service.BookingOccurrenceService;
import mss301.bookingservice.service.BookingOutboxService;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.support.TestPlatformServer;
import mss301.bookingservice.support.TestPlatformServer.CaregiverIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import mss301.bookingservice.client.MediaClient;
import mss301.bookingservice.client.dto.MediaClientResponse;
import mss301.bookingservice.client.dto.DownloadUrlClientResponse;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.enums.NotificationType;
import org.springframework.mock.web.MockMultipartFile;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp qua MockMvc: JWT thật (RS256, JWKS từ TestPlatformServer), Feign thật tới Identity / CareRecipient /
 * Provider giả. Mặc định H2; -Dbooking.testing.postgres=true chạy trên PostgreSQL thật (schema/booking.sql + validate).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingApiTests {

    static final String PG_URL = System.getProperty("booking.testing.postgres-url", "jdbc:postgresql://localhost:55496/postgres");
    static final String PG_USER = "postgres";
    static final String PG_PASSWORD = "booking_test_only";
    static final TestPlatformServer PLATFORM = TestPlatformServer.get();
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    static {
        // Như BookingServiceApplication.main: JVM chạy UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        boolean postgres = Boolean.getBoolean("booking.testing.postgres");
        String schema = "booking_test_" + UUID.randomUUID().toString().replace("-", "");
        if (postgres) {
            createSchema(schema);
        }
        registry.add("spring.datasource.url", () -> postgres
                ? PG_URL + "?currentSchema=" + schema
                : "jdbc:h2:mem:booking;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> postgres ? PG_USER : "sa");
        registry.add("spring.datasource.password", () -> postgres ? PG_PASSWORD : "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> postgres ? "validate" : "create-drop");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> postgres
                ? "file:./schema/booking.sql"
                : "classpath:h2-functions.sql");
        registry.add("booking.jwt.issuer", () -> TestPlatformServer.ISSUER);
        registry.add("booking.jwt.jwks-uri", PLATFORM::jwksUri);
        registry.add("booking.identity.url", PLATFORM::baseUrl);
        registry.add("booking.care-recipient.url", PLATFORM::baseUrl);
        registry.add("booking.provider.url", PLATFORM::baseUrl);
        registry.add("booking.care-catalog.url", PLATFORM::baseUrl);
        registry.add("booking.payment.url", PLATFORM::baseUrl);
        registry.add("booking.service-client.client-id", () -> "booking");
        registry.add("booking.service-client.client-secret", () -> "booking-test-secret");
        // Scheduler không chạy trong lúc test; test gọi BookingService trực tiếp.
        registry.add("booking.schedule.expiry-scan-interval", () -> "1h");
        registry.add("booking.notification.admin-user-ids", ADMIN_USER_ID::toString);
    }

    // Admin cấu hình nhận thông báo vận hành (khiếu nại chuyển cấp, outbox FAILED).
    static final UUID ADMIN_USER_ID = UUID.randomUUID();

    static void createSchema(String schema) {
        try (Connection connection = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    NotificationService notificationService;

    @MockitoBean
    MediaClient mediaClient;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    BookingEventRepository bookingEventRepository;

    @Autowired
    BookingOccurrenceRepository bookingOccurrenceRepository;

    @Autowired
    BookingOutboxRepository bookingOutboxRepository;

    @Autowired
    CareTaskRepository careTaskRepository;

    @Autowired
    BookingOutboxService bookingOutboxService;

    @Autowired
    BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    BookingService bookingExpiryService;

    @Autowired
    BookingOccurrenceService bookingOccurrenceService;

    @Autowired
    CareSessionService careSessionService;

    @Autowired
    mss301.bookingservice.config.BookingProperties bookingProperties;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @AfterEach
    void resetPlatform() {
        PLATFORM.setIdentityFailing(false);
        PLATFORM.setCareRecipientFailing(false);
        PLATFORM.setProviderFailing(false);
        PLATFORM.setCatalogFailing(false);
        PLATFORM.setPaymentFailing(false);
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===== Xác thực / phân quyền theo role =====

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mockMvc.perform(get("/api/v1/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id"));
        UUID userId = UUID.randomUUID();
        String refreshToken = PLATFORM.token(userId, null, List.of("careconnect-identity", "careconnect-api"), "refresh",
                List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
        String identityOnlyToken = PLATFORM.token(userId, null, List.of("careconnect-identity"), "access", List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + identityOnlyToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforcedPerApi() throws Exception {
        Scenario scenario = newScenario();
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/bookings").header("Authorization",
                        bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/" + UUID.randomUUID() + "/accept")
                        .header("Authorization", bearer(scenario.customerId(), null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        // Path tách theo role đã bỏ (2026-10-22): /bookings/admin, /bookings/center không còn là danh sách.
        mockMvc.perform(get("/api/v1/bookings/admin").header("Authorization", bearer(UUID.randomUUID(), null, "ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/booking-occurrences").param("from", "2026-01-01").param("to", "2026-01-02")
                        .header("Authorization", bearer(scenario.customerId(), null, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ===== Tạo booking =====

    @Test
    void createWeeklyBookingGeneratesOccurrencesInUtcAndStoresSnapshot() throws Exception {
        Scenario scenario = newScenario();
        String requestId = "req-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", customerBearer(scenario))
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, scenario.caregiver().caregiverId(), 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.customerId").value(scenario.customerId().toString()))
                .andExpect(jsonPath("$.result.caregiverId").value(scenario.caregiver().caregiverId().toString()))
                .andExpect(jsonPath("$.result.caregiverUserId").value(scenario.caregiver().userId().toString()))
                .andExpect(jsonPath("$.result.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.result.daysOfWeek.length()").value(3))
                .andExpect(jsonPath("$.result.recipientSnapshot.fullName").value("Bà Lan"))
                .andExpect(jsonPath("$.result.recipientSnapshot.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.result.recipientSnapshot.activeMedications[0].medicationName").value("Amlodipine"))
                .andExpect(jsonPath("$.result.recipientSnapshot.primaryEmergencyContact.phoneNumber").value("0912345678"))
                .andExpect(jsonPath("$.result.serviceAddress").value("12 Lý Thường Kiệt, Phường Hàng Bài, Hoàn Kiếm, Hà Nội"))
                .andExpect(jsonPath("$.result.services.length()").value(1))
                // Giá luôn từ Catalog (gói mặc định giả: HOUR 100.000đ): 6 buổi × 2 giờ = 1.200.000đ; cọc do Payment quyết.
                .andExpect(jsonPath("$.result.services[0].serviceName").exists())
                .andExpect(jsonPath("$.result.totalAmount").value(1200000))
                .andExpect(jsonPath("$.result.depositAmount").doesNotExist())
                .andExpect(jsonPath("$.result.customer").doesNotExist())
                .andExpect(jsonPath("$.result.occurrences.length()").value(6))
                .andReturn();
        JsonNode booking = readResult(result);
        assertThat(booking.get("bookingCode").asString()).matches("BK-\\d{8}-[A-Z2-9]{6}");
        JsonNode occurrences = booking.get("occurrences");
        // 08:00–10:00 giờ Việt Nam = 01:00–03:00 UTC; Thứ Hai, Tư, Sáu của hai tuần
        assertThat(occurrences.get(0).get("startAt").asString()).endsWith("T01:00:00Z");
        assertThat(occurrences.get(0).get("endAt").asString()).endsWith("T03:00:00Z");
        assertThat(occurrences.get(0).get("durationMinutes").asInt()).isEqualTo(120);
        assertThat(occurrences.get(0).get("occurrenceNo").asInt()).isEqualTo(1);
        assertThat(occurrences.get(5).get("occurrenceNo").asInt()).isEqualTo(6);
        assertThat(occurrences.get(0).get("status").asString()).isEqualTo("SCHEDULED");
        assertThat(occurrences.get(0).get("caregiverId").asString()).isEqualTo(scenario.caregiver().caregiverId().toString());
        assertThat(PLATFORM.lastCareRecipientRequestId()).isEqualTo(requestId);
        UUID bookingId = UUID.fromString(booking.get("id").asString());
        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findAllByBooking(bookingId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isNull();
        assertThat(history.getFirst().getToStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(history.getFirst().getChangedBy()).isEqualTo(scenario.customerId());

        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(6));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].recipientName").value("Bà Lan"))
                .andExpect(jsonPath("$.result.items[0].customer").doesNotExist());
    }

    @Test
    void idempotencyKeyReturnsSameBookingAndRejectsDifferentBody() throws Exception {
        Scenario scenario = newScenario();
        String key = "key-" + UUID.randomUUID();
        Map<String, Object> body = weeklyBody(scenario, null, 0);
        String firstId = readResult(createBooking(scenario, body, key).andExpect(status().isOk()).andReturn())
                .get("id").asString();
        String secondId = readResult(createBooking(scenario, body, key).andExpect(status().isOk()).andReturn())
                .get("id").asString();
        assertThat(secondId).isEqualTo(firstId);
        Map<String, Object> changedBody = weeklyBody(scenario, null, 0);
        changedBody.put("customerNote", "Nội dung khác");
        createBooking(scenario, changedBody, key)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.IDEMPOTENCY_KEY_REUSED.getCode()));
        createBooking(scenario, body, "bad key with spaces")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_IDEMPOTENCY_KEY.getCode()));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void recipientAccessAndStatusAreChecked() throws Exception {
        Scenario scenario = newScenario();
        UUID stranger = PLATFORM.addUser("Người lạ", "0900000000");
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(stranger, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
        // VIEWER không đủ quyền đặt lịch (tối thiểu EDITOR) → vẫn 404, không lộ hồ sơ
        UUID viewer = PLATFORM.addUser("Người xem", "0900000001");
        PLATFORM.addRecipientManager(scenario.recipientId(), viewer, "VIEWER");
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(viewer, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isNotFound());
        UUID editor = PLATFORM.addUser("Người sửa", "0900000002");
        PLATFORM.addRecipientManager(scenario.recipientId(), editor, "EDITOR");
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(editor, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isOk());

        UUID inactiveRecipient = PLATFORM.addRecipient(scenario.customerId(), "Ông Tạm Ngưng", "INACTIVE");
        Map<String, Object> inactiveBody = weeklyBody(scenario, null, 0);
        inactiveBody.put("recipientId", inactiveRecipient.toString());
        createBooking(scenario, inactiveBody, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_ACTIVE.getCode()));
    }

    @Test
    void careRecipientDownGives503AndCreatesNothing() throws Exception {
        Scenario scenario = newScenario();
        PLATFORM.setCareRecipientFailing(true);
        createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_RECIPIENT_UNAVAILABLE.getCode()));
        PLATFORM.setCareRecipientFailing(false);
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void centerAndCaregiverAreVerifiedWithProvider() throws Exception {
        Scenario scenario = newScenario();
        Map<String, Object> inactiveCenterBody = weeklyBody(scenario, null, 0);
        inactiveCenterBody.put("centerId", PLATFORM.addCenter("SUSPENDED").toString());
        createBooking(scenario, inactiveCenterBody, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_NOT_ACTIVE.getCode()));
        Map<String, Object> unknownCenterBody = weeklyBody(scenario, null, 0);
        unknownCenterBody.put("centerId", UUID.randomUUID().toString());
        createBooking(scenario, unknownCenterBody, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_NOT_FOUND.getCode()));

        CaregiverIds otherCenterCaregiver = PLATFORM.addActiveCaregiver(PLATFORM.addCenter("ACTIVE"));
        createBooking(scenario, weeklyBody(scenario, otherCenterCaregiver.caregiverId(), 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_IN_CENTER.getCode()));
        CaregiverIds unverified = PLATFORM.addCaregiver(scenario.centerId(), "PENDING", "INACTIVE", null);
        createBooking(scenario, weeklyBody(scenario, unverified.caregiverId(), 0), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_VERIFIED.getCode()));
        CaregiverIds noSkills = PLATFORM.addCaregiver(scenario.centerId(), "VERIFIED", "ACTIVE", List.of());
        createBooking(scenario, weeklyBody(scenario, noSkills.caregiverId(), 0), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SERVICE_NOT_SUPPORTED.getCode()));
        createBooking(scenario, weeklyBody(scenario, UUID.randomUUID(), 0), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_FOUND.getCode()));

        PLATFORM.setProviderFailing(true);
        createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.PROVIDER_UNAVAILABLE.getCode()));
    }

    @Test
    void validationErrorsUseErrorCodes() throws Exception {
        Scenario scenario = newScenario();
        Map<String, Object> noServices = weeklyBody(scenario, null, 0);
        noServices.put("services", List.of());
        createBooking(scenario, noServices, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SERVICES_REQUIRED.getCode()));
        Map<String, Object> noDays = weeklyBody(scenario, null, 0);
        noDays.remove("daysOfWeek");
        createBooking(scenario, noDays, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.DAYS_OF_WEEK_REQUIRED.getCode()));
        Map<String, Object> massAssignment = weeklyBody(scenario, null, 0);
        massAssignment.put("status", "CONFIRMED");
        createBooking(scenario, massAssignment, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.getCode()));
        Map<String, Object> pastDate = weeklyBody(scenario, null, 0);
        pastDate.put("startDate", LocalDate.now(ZONE).minusDays(1).toString());
        createBooking(scenario, pastDate, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.START_DATE_IN_PAST.getCode()));
    }

    // ===== Trùng lịch =====

    @Test
    void caregiverAndRecipientConflictsAreRejectedButAdjacentSlotsAreAllowed() throws Exception {
        Scenario scenario = newScenario();
        createBooking(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0), null)
                .andExpect(status().isOk());
        // Cùng caregiver, người bệnh khác, giờ giao nhau → trùng caregiver
        UUID otherRecipient = PLATFORM.addRecipient(scenario.customerId(), "Ông Nam", "ACTIVE");
        Map<String, Object> overlapping = weeklyBody(scenario, scenario.caregiver().caregiverId(), 0);
        overlapping.put("recipientId", otherRecipient.toString());
        overlapping.put("startTime", "09:00");
        overlapping.put("endTime", "11:00");
        createBooking(scenario, overlapping, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT.getCode()));
        // Cùng người bệnh, không caregiver, giờ giao nhau → trùng người bệnh (mọi trung tâm)
        Map<String, Object> sameRecipient = weeklyBody(scenario, null, 0);
        sameRecipient.put("centerId", PLATFORM.addCenter("ACTIVE").toString());
        sameRecipient.put("startTime", "09:30");
        sameRecipient.put("endTime", "10:30");
        createBooking(scenario, sameRecipient, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_SCHEDULE_CONFLICT.getCode()));
        // Nối tiếp đúng giờ kết thúc (10:00) không tính là trùng
        Map<String, Object> adjacent = weeklyBody(scenario, scenario.caregiver().caregiverId(), 0);
        adjacent.put("recipientId", otherRecipient.toString());
        adjacent.put("startTime", "10:00");
        adjacent.put("endTime", "12:00");
        createBooking(scenario, adjacent, null).andExpect(status().isOk());
    }

    // ===== Luồng trung tâm → đặt cọc → caregiver =====

    @Test
    void fullFlowAcceptDepositThenCaregiverSeesScheduleAndRecipient() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        LocalDate from = LocalDate.now(ZONE);
        LocalDate to = from.plusDays(40);

        // PENDING: caregiver chưa được giao → không thấy gì; truy cập hồ sơ bị từ chối
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_REQUIRED.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", scenario.caregiver().caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.result.caregiverUserId").value(scenario.caregiver().userId().toString()))
                .andExpect(jsonPath("$.result.depositDueAt").exists())
                .andExpect(jsonPath("$.result.customer.fullName").value("Khách Hàng"))
                .andExpect(jsonPath("$.result.customer.phoneNumber").value("0987654321"))
                .andExpect(jsonPath("$.result.occurrences[0].caregiverId").value(scenario.caregiver().caregiverId().toString()));
        // Chưa đặt cọc: caregiver vẫn chưa thấy lịch / hồ sơ
        mockMvc.perform(get("/api/v1/booking-occurrences").param("from", from.toString()).param("to", to.toString())
                        .header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentReference", "PAY-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.confirmedAt").exists());
        // Gọi lại: idempotent
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences.length()").value(6));

        MvcResult schedule = mockMvc.perform(get("/api/v1/booking-occurrences").param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(6))
                .andExpect(jsonPath("$.result.items[0].recipientName").value("Bà Lan"))
                .andExpect(jsonPath("$.result.items[0].bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.items[0].recipientSnapshot").doesNotExist())
                .andReturn();
        String occurrenceId = readResult(schedule).get("items").get(0).get("id").asString();
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientSnapshot.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.result.serviceAddress").exists());
        CaregiverIds otherCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId)
                        .header("Authorization", bearer(otherCaregiver.userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NOT_FOUND.getCode()));
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), true);
        assertCaregiverAccess(otherCaregiver.userId(), scenario.recipientId(), false);
        assertCaregiverAccess(scenario.caregiver().userId(), UUID.randomUUID(), false);

        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findAllByBooking(UUID.fromString(bookingId));
        assertThat(history).extracting(BookingStatusHistory::getToStatus)
                .containsExactly(BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED);
        assertThat(history.get(2).getReason()).isEqualTo("paymentReference=PAY-123");
        assertThat(history.get(2).getChangedBy()).isNull();
    }

    @Test
    void managerOnlySeesOwnCenterAndNeedsCenterClaim() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        UUID otherCenter = PLATFORM.addCenter("ACTIVE");
        String otherManager = bearer(UUID.randomUUID(), otherCenter, "CENTER_MANAGER");
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", otherManager))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_NOT_FOUND.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reject")
                        .header("Authorization", otherManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Không phải của tôi"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", otherManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        // Manager không lách phạm vi bằng query centerId — trung tâm luôn lấy từ token.
        mockMvc.perform(get("/api/v1/bookings").param("centerId", scenario.centerId().toString())
                        .header("Authorization", otherManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        // Khách không lách phạm vi bằng customerId của người khác.
        UUID strangerId = PLATFORM.addUser("Người lạ", "0900000000");
        mockMvc.perform(get("/api/v1/bookings").param("customerId", scenario.customerId().toString())
                        .header("Authorization", bearer(strangerId, null, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences").header("Authorization", otherManager))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(6));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", otherManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Không phải của tôi"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/bookings")
                        .header("Authorization", bearer(UUID.randomUUID(), null, "CENTER_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_SCOPE_REQUIRED.getCode()));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].customer.fullName").value("Khách Hàng"))
                .andExpect(jsonPath("$.result.items[0].recipientName").value("Bà Lan"));
        // Customer khác không thấy booking → 404
        UUID otherCustomer = PLATFORM.addUser("Khách khác", "0911111111");
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(otherCustomer, null, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", bearer(otherCustomer, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Thử hủy"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectReleasesCaregiverAndCancelsOccurrences() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reject")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.REASON_REQUIRED.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reject")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Không đủ nhân sự"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"))
                .andExpect(jsonPath("$.result.rejectedReason").value("Không đủ nhân sự"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"));
        // Caregiver được giải phóng: đặt lại đúng khung giờ đó thành công
        createBooking(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0), null)
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
    }

    @Test
    void customerCancelAndInvalidTransitions() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Đổi kế hoạch"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.cancelReason").value("Đổi kế hoạch"))
                .andExpect(jsonPath("$.result.cancelledBy").value(scenario.customerId().toString()))
                .andExpect(jsonPath("$.result.occurrences[5].status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Hủy lần hai"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Đã hủy rồi"))))
                .andExpect(status().isConflict());
    }

    // ===== Khách hủy từng buổi / cả booking + preview (BK-03) =====

    @Test
    void customerPreviewsAndCancelsSingleOccurrenceWithToken() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 3));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        UUID second = occurrences.get(1).getId();
        UUID third = occurrences.get(2).getId();
        BookingOccurrence secondOccurrence = occurrences.get(1);
        secondOccurrence.setStartAt(Instant.now().plus(Duration.ofHours(48)));
        secondOccurrence.setEndAt(secondOccurrence.getStartAt().plus(Duration.ofHours(2)));
        bookingOccurrenceRepository.save(secondOccurrence);

        // Buổi còn 48h → mốc 24–72h, hoàn 50%.
        MvcResult preview = previewCancellation(customerBearer(scenario), bookingId, List.of(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].rule").value("TIER_24_72"))
                .andExpect(jsonPath("$.result.items[0].startAt").exists())
                .andExpect(jsonPath("$.result.totalRefund").value(50000))
                .andReturn();
        String previewToken = readResult(preview).get("previewToken").asString();
        previewCancellation(customerBearer(scenario), bookingId, List.of(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NOT_IN_BOOKING.getCode()));
        previewCancellation(customerBearer(newScenario()), bookingId, List.of(second))
                .andExpect(status().isNotFound());

        cancelBooking(customerBearer(scenario), bookingId, List.of(second), previewToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences[?(@.id == '" + second + "')].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.occurrences[?(@.id == '" + second + "')].cancelledByParty").value("CUSTOMER"))
                .andExpect(jsonPath("$.result.refund.reason").value("CUSTOMER_CANCEL"))
                .andExpect(jsonPath("$.result.refund.totalRefund").value(50000));
        assertThat(PLATFORM.refundKeys()).filteredOn(key -> key.startsWith("REFUND:CUSTOMER_CANCEL:" + bookingId)).hasSize(1);
        cancelBooking(customerBearer(scenario), bookingId, List.of(second), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NOT_CANCELLABLE.getCode()));

        // Số tiền đổi sau preview (vd qua mốc) → 409, không hủy gì.
        String thirdToken = readResult(previewCancellation(customerBearer(scenario), bookingId, List.of(third))
                .andExpect(status().isOk()).andReturn()).get("previewToken").asString();
        PLATFORM.setRefundPercentOverride(10);
        try {
            cancelBooking(customerBearer(scenario), bookingId, List.of(third), thirdToken)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.CANCELLATION_PREVIEW_EXPIRED.getCode()));
        } finally {
            PLATFORM.setRefundPercentOverride(null);
        }
        assertThat(bookingOccurrenceRepository.findById(third).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.SCHEDULED);

        // Buổi đang diễn ra không hủy được.
        moveIntoCheckInWindow(first);
        startSession(bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"), first)
                .andExpect(status().isOk());
        cancelBooking(customerBearer(scenario), bookingId, List.of(first), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NOT_CANCELLABLE.getCode()));
    }

    @Test
    void cancelAllWhilePaymentDownStillCancelsAndRetriesRefund() throws Exception {
        Scenario scenario = newScenario();
        String pendingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        UUID pendingOccurrence = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(pendingId)).getFirst().getId();
        cancelBooking(customerBearer(scenario), pendingId, List.of(pendingOccurrence), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CANCEL_SCOPE_NOT_ALLOWED.getCode()));
        acceptAndConfirm(scenario, pendingId, null);

        PLATFORM.setPaymentFailing(true);
        try {
            cancelBooking(customerBearer(scenario), pendingId, null, null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.result.refund.occurrenceIds.length()").value(2));
        } finally {
            PLATFORM.setPaymentFailing(false);
        }
        BookingOutbox outbox = bookingOutboxRepository.findAllByAggregateIdAndDeletedAtIsNull(UUID.fromString(pendingId))
                .getFirst();
        assertThat(outbox.getEventType()).isEqualTo(OutboxEventType.REFUND);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        outbox.setNextAttemptAt(Instant.now().minusSeconds(1));
        bookingOutboxRepository.save(outbox);
        bookingOutboxService.dispatch(outbox.getId());
        assertThat(PLATFORM.refundKeys()).filteredOn(key -> key.startsWith("REFUND:CUSTOMER_CANCEL:" + pendingId)).hasSize(1);
    }

    // ===== Chống trùng lịch tầng DB (BK-09) — chỉ PostgreSQL (H2 không có exclusion constraint) =====

    @Test
    @EnabledIfSystemProperty(named = "booking.testing.postgres", matches = "true")
    void databaseRejectsOverlappingActiveSlotsOfSameCaregiver() throws Exception {
        Scenario scenario = newScenario();
        createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        Scenario otherCustomer = newScenario();
        String otherId = createAndGetId(otherCustomer, dailyBody(otherCustomer, otherCustomer.caregiver().caregiverId(), 1));
        // Bỏ qua bước kiểm + advisory lock ở service: ghi thẳng caregiver trùng giờ → DB phải chặn.
        BookingOccurrence occurrence = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(otherId)).getFirst();
        occurrence.setCaregiverId(scenario.caregiver().caregiverId());
        occurrence.setCaregiverUserId(scenario.caregiver().userId());
        assertThatThrownBy(() -> bookingOccurrenceRepository.saveAndFlush(occurrence))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_booking_occurrences_caregiver_slot");
    }

    // ===== Danh sách: lọc, sắp xếp, phân trang, thống kê buổi (BK-06) =====

    @Test
    void listSortsByWhitelistFiltersByCodeAndReportsOccurrenceCounts() throws Exception {
        Scenario scenario = newScenario();
        Map<String, Object> laterBody = dailyBody(scenario, scenario.caregiver().caregiverId(), 2);
        laterBody.put("startDate", LocalDate.now(ZONE).plusDays(30).toString());
        laterBody.put("endDate", LocalDate.now(ZONE).plusDays(31).toString());
        String laterId = createAndGetId(scenario, laterBody);
        String earlierId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 3));
        String earlierCode = bookingRepository.findById(UUID.fromString(earlierId)).orElseThrow().getBookingCode();

        mockMvc.perform(get("/api/v1/bookings").param("sort", "startDate,asc").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].id").value(earlierId))
                .andExpect(jsonPath("$.result.items[0].totalCount").value(3))
                .andExpect(jsonPath("$.result.items[0].completedCount").value(0))
                .andExpect(jsonPath("$.result.items[0].nextOccurrenceAt").exists())
                .andExpect(jsonPath("$.result.items[1].id").value(laterId));
        mockMvc.perform(get("/api/v1/bookings").param("sort", "startDate,asc").param("size", "1").param("page", "1")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(laterId));
        mockMvc.perform(get("/api/v1/bookings").param("sort", "customerId,asc").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_SORT.getCode()));
        mockMvc.perform(get("/api/v1/bookings").param("keyword", earlierCode.substring(earlierCode.length() - 6).toLowerCase())
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(earlierId));
        mockMvc.perform(get("/api/v1/bookings/" + earlierId + "/occurrences").param("size", "2").param("page", "1")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].occurrenceNo").value(3));
        mockMvc.perform(get("/api/v1/bookings/" + earlierId + "/occurrences").param("status", "COMPLETED")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    // ===== Lịch sử booking + audit đổi caregiver (BK-05) =====

    @Test
    void historyMergesStatusChangesAndCaregiverChangesAndListFindsOccurrenceCaregiver() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        CaregiverIds newCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + occurrences.get(1).getId() + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", newCaregiver.caregiverId().toString()))))
                .andExpect(status().isOk());

        MvcResult history = mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/history")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].eventType").value("BOOKING_STATUS_CHANGED"))
                .andExpect(jsonPath("$.result.items[0].toStatus").value("PENDING"))
                .andReturn();
        JsonNode items = readResult(history).get("items");
        JsonNode caregiverChanged = null;
        for (JsonNode item : items) {
            if ("CAREGIVER_CHANGED".equals(item.get("eventType").asString())) {
                caregiverChanged = item;
            }
        }
        assertThat(caregiverChanged).isNotNull();
        assertThat(caregiverChanged.get("occurrenceId").asString()).isEqualTo(occurrences.get(1).getId().toString());
        assertThat(caregiverChanged.get("oldCaregiverId").asString()).isEqualTo(scenario.caregiver().caregiverId().toString());
        assertThat(caregiverChanged.get("newCaregiverId").asString()).isEqualTo(newCaregiver.caregiverId().toString());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/history").param("size", "1").param("page", "1")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(items.size()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/history").header("Authorization", customerBearer(newScenario())))
                .andExpect(status().isNotFound());

        // Caregiver được giao riêng một buổi vẫn lọc ra booking.
        mockMvc.perform(get("/api/v1/bookings").param("caregiverId", newCaregiver.caregiverId().toString())
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    // ===== Hủy hệ thống khi khóa trung tâm / caregiver (BK-04) =====

    @Test
    void lockedCenterCancelsBookingsOnceAndDeactivatedCaregiverFlagsOccurrences() throws Exception {
        Scenario scenario = newScenario();
        String pendingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        CaregiverIds otherCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        Map<String, Object> confirmedBody = dailyBody(scenario, otherCaregiver.caregiverId(), 2);
        confirmedBody.put("startDate", LocalDate.now(ZONE).plusDays(20).toString());
        confirmedBody.put("endDate", LocalDate.now(ZONE).plusDays(21).toString());
        String confirmedId = createAndGetId(scenario, confirmedBody);
        acceptAndConfirm(scenario, confirmedId, null);

        systemCancel(customerBearer(scenario), Map.of("reason", "CENTER_LOCKED", "centerId", scenario.centerId().toString()))
                .andExpect(status().isForbidden());
        systemCancel(serviceBearer(), Map.of("reason", "CENTER_LOCKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_ID_REQUIRED.getCode()));
        for (int call = 1; call <= 2; call++) {
            systemCancel(serviceBearer(), Map.of("reason", "CENTER_LOCKED", "centerId", scenario.centerId().toString(),
                            "note", "Trung tâm bị khóa"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.affectedBookings").value(call == 1 ? 2 : 0))
                    .andExpect(jsonPath("$.result.affectedOccurrences").value(call == 1 ? 3 : 0));
        }
        assertThat(bookingRepository.findById(UUID.fromString(pendingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(bookingRepository.findById(UUID.fromString(confirmedId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(PLATFORM.refundKeys()).filteredOn(key -> key.startsWith("REFUND:CENTER_LOCKED:" + confirmedId)).hasSize(1);
        assertThat(PLATFORM.refundKeys()).noneMatch(key -> key.contains(pendingId));

        // Khóa caregiver: không hủy, đánh dấu buổi tương lai; manager đổi người thì bỏ cờ.
        Scenario second = newScenario();
        String bookingId = createAndGetId(second, dailyBody(second, second.caregiver().caregiverId(), 2));
        acceptAndConfirm(second, bookingId, null);
        for (int call = 1; call <= 2; call++) {
            systemCancel(serviceBearer(), Map.of("reason", "CAREGIVER_DEACTIVATED",
                            "caregiverId", second.caregiver().caregiverId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.affectedBookings").value(call == 1 ? 1 : 0))
                    .andExpect(jsonPath("$.result.affectedOccurrences").value(call == 1 ? 2 : 0));
        }
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", managerBearer(second)))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences[0].needsReassignment").value(true));
        UUID firstOccurrence = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + firstOccurrence + "/caregiver")
                        .header("Authorization", managerBearer(second))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId",
                                PLATFORM.addActiveCaregiver(second.centerId()).caregiverId().toString()))))
                .andExpect(status().isOk());
        assertThat(bookingOccurrenceRepository.findById(firstOccurrence).orElseThrow().getNeedsReassignment()).isFalse();
        // H-11: caregiver cũ không check-in được buổi còn chờ đổi người; khách được báo.
        UUID secondOccurrence = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getLast().getId();
        moveIntoCheckInWindow(secondOccurrence);
        startSession(bearer(second.caregiver().userId(), second.centerId(), "CAREGIVER"), secondOccurrence)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NEEDS_REASSIGNMENT.getCode()));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", customerBearer(second)).param("size", "100"))
                .andExpect(jsonPath("$.result.items[?(@.type == 'CAREGIVER_UNASSIGNED')]").isNotEmpty());
    }

    // M-07: CareRecipientService hỏi số booking chưa kết thúc của người bệnh (chỉ token SERVICE).
    @Test
    void recipientActivityCountsUnfinishedBookingsForServiceTokensOnly() throws Exception {
        Scenario scenario = newScenario();
        createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        mockMvc.perform(get("/api/v1/bookings/recipient-activity").param("recipientId", scenario.recipientId().toString())
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.unfinishedBookings").value(1));
        mockMvc.perform(get("/api/v1/bookings/recipient-activity").param("recipientId", UUID.randomUUID().toString())
                        .header("Authorization", serviceBearer()))
                .andExpect(jsonPath("$.result.unfinishedBookings").value(0));
        mockMvc.perform(get("/api/v1/bookings/recipient-activity").param("recipientId", scenario.recipientId().toString())
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
    }

    ResultActions systemCancel(String bearer, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/system-cancellations")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    ResultActions previewCancellation(String bearer, String bookingId, List<UUID> occurrenceIds) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancellation-previews")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("occurrenceIds", occurrenceIds))));
    }

    ResultActions cancelBooking(String bearer, String bookingId, List<UUID> occurrenceIds, String previewToken)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", "Gia đình đổi kế hoạch");
        if (occurrenceIds != null) {
            body.put("occurrenceIds", occurrenceIds);
        }
        if (previewToken != null) {
            body.put("previewToken", previewToken);
        }
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    @Test
    void managerChangesCaregiverForBookingAndSingleOccurrence() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(replacement.caregiverId().toString()))
                .andExpect(jsonPath("$.result.caregiverUserId").value(replacement.userId().toString()))
                .andExpect(jsonPath("$.result.occurrences[3].caregiverId").value(replacement.caregiverId().toString()));
        assertCaregiverAccess(replacement.userId(), scenario.recipientId(), true);
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);

        // Đổi riêng một buổi sang caregiver đang bận cùng giờ → trùng lịch
        Scenario busy = newScenarioInCenter(scenario.centerId());
        String busyBookingId = createAndGetId(busy, weeklyBody(busy, busy.caregiver().caregiverId(), 0));
        String occurrenceId = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                        .header("Authorization", customerBearer(scenario)))
                .andReturn()).get("items").get(0).get("id").asString();
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiver().caregiverId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + busyBookingId + "/reject")
                        .header("Authorization", managerBearer(busy))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Giải phóng lịch"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiver().caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(busy.caregiver().caregiverId().toString()));
        // Caregiver của một buổi riêng cũng được xem hồ sơ người bệnh
        assertCaregiverAccess(busy.caregiver().userId(), scenario.recipientId(), true);
        // Buổi không thuộc booking trên path → 404 (không đổi được buổi qua booking khác của cùng trung tâm).
        mockMvc.perform(patch("/api/v1/bookings/" + busyBookingId + "/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void centerCancelInSpecialCase() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Caregiver ốm đột xuất"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.occurrences[0].cancelledByParty").value("CENTER"))
                .andExpect(jsonPath("$.result.refund.reason").value("CENTER_CANCEL"));
        assertThat(PLATFORM.refundKeys()).filteredOn(key -> key.startsWith("REFUND:CENTER_CANCEL:" + bookingId)).hasSize(1);
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
    }

    // BK-09: hai instance scheduler — instance B gặp booking đang bị A khóa thì bỏ qua ngay (SKIP LOCKED), không chờ và
    // không xử lý trùng; A nhả khóa thì lượt quét sau xử lý bình thường. H2 không có SKIP LOCKED nên chỉ chạy PostgreSQL.
    @Test
    @EnabledIfSystemProperty(named = "booking.testing.postgres", matches = "true")
    void expirySkipsBookingLockedByAnotherInstance() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.setAcceptanceDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread otherInstance = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            bookingRepository.findForUpdate(booking.getId()).orElseThrow();
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        otherInstance.start();
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
        long startedAt = System.nanoTime();
        bookingExpiryService.expire(booking.getId());
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(5));
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
        release.countDown();
        otherInstance.join(10_000);
        bookingExpiryService.expire(booking.getId());
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void overduePendingBookingExpiresAndReleasesSchedule() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(booking.getAcceptanceDueAt()).isAfter(Instant.now());
        booking.setAcceptanceDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        assertThat(bookingExpiryService.findExpirableIds()).contains(booking.getId());
        bookingExpiryService.expire(booking.getId());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"));
        // Gọi lại không đổi gì; khung giờ đã được giải phóng
        bookingExpiryService.expire(booking.getId());
        createBooking(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0), null)
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict());
    }

    // ===== Admin / API nội bộ / hạ tầng =====

    @Test
    void adminSeesAllBookingsAndToleratesIdentityOutage() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        String adminBearer = bearer(UUID.randomUUID(), null, "ADMIN");
        mockMvc.perform(get("/api/v1/bookings").param("centerId", scenario.centerId().toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].customer.fullName").value("Khách Hàng"));
        PLATFORM.setIdentityFailing(true);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(bookingId))
                .andExpect(jsonPath("$.result.customer").doesNotExist());
        mockMvc.perform(get("/api/v1/bookings").param("page", "-1").header("Authorization", adminBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGE.getCode()));
    }

    @Test
    void interServiceEndpointsRequireAuthentication() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        mockMvc.perform(get("/api/v1/bookings/caregiver-access")
                .param("caregiverUserId", scenario.caregiver().userId().toString())
                .param("recipientId", scenario.recipientId().toString()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());

        // Chỉ token SERVICE (CareRecipientService) được hỏi quyền caregiver — user thường, kể cả ADMIN, bị 403.
        for (String userBearer : List.of(customerBearer(scenario), adminBearer(),
                bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"))) {
            mockMvc.perform(get("/api/v1/bookings/caregiver-access")
                            .param("caregiverUserId", scenario.caregiver().userId().toString())
                            .param("recipientId", scenario.recipientId().toString())
                            .header("Authorization", userBearer))
                    .andExpect(status().isForbidden());
        }
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);

        String occurrenceId = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get("items").get(0).get("id").asString();
        for (String fullAccessBearer : List.of(adminBearer(), serviceBearer(), customerBearer(scenario), managerBearer(scenario))) {
            mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId)
                            .header("Authorization", fullAccessBearer))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.bookingId").value(bookingId))
                    .andExpect(jsonPath("$.result.recipientSnapshot.fullName").value("Bà Lan"));
        }
    }

    @Test
    void occurrenceDetailIsScopedPerRole() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        String occurrenceId = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get("items").get(0).get("id").asString();
        Scenario otherCustomer = newScenario();
        // Khách K2 / manager trung tâm khác / caregiver khi booking chưa xác nhận → 404 như không tồn tại.
        for (String outsiderBearer : List.of(customerBearer(otherCustomer), managerBearer(otherCustomer),
                bearer(UUID.randomUUID(), null, "CENTER_MANAGER"),
                bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"))) {
            mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", outsiderBearer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCE_NOT_FOUND.getCode()));
        }
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId))
                .andExpect(status().isUnauthorized());

        // Booking CANCELLED: caregiver vẫn thấy buổi nhưng không còn snapshot y tế; quyền xem hồ sơ bị thu hồi (SEC-09).
        acceptAndConfirm(scenario, bookingId, null);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientSnapshot.fullName").value("Bà Lan"));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Trung tâm tạm ngưng"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.result.recipientSnapshot").doesNotExist());
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
    }

    // M-11: admin hủy thay nền tảng — booking đã cọc hoàn như trung tâm hủy, booking PENDING hủy được (không hoàn).
    @Test
    void adminCancelsAnyBookingWithCenterCancelRefund() throws Exception {
        Scenario scenario = newScenario();
        String confirmedId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, confirmedId, null);
        mockMvc.perform(post("/api/v1/bookings/" + confirmedId + "/cancel").header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Khiếu nại an toàn"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CANCELLED"));
        assertThat(PLATFORM.refundKeys()).filteredOn(key -> key.startsWith("REFUND:CENTER_CANCEL:" + confirmedId)).hasSize(1);
        assertThat(bookingOccurrenceRepository.findAllByBooking(UUID.fromString(confirmedId)).getFirst().getCancelledByParty())
                .isEqualTo(CancelledBy.ADMIN);

        Scenario other = newScenario();
        String pendingId = createAndGetId(other, dailyBody(other, other.caregiver().caregiverId(), 1));
        mockMvc.perform(post("/api/v1/bookings/" + pendingId + "/cancel").header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Đặt trùng"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CANCELLED"));
        mockMvc.perform(post("/api/v1/bookings/" + pendingId + "/cancel").header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Lần hai"))))
                .andExpect(status().isConflict());
    }

    @Test
    void cookieWriteRequiresCsrf() throws Exception {
        Scenario scenario = newScenario();
        String token = PLATFORM.accessToken(scenario.customerId(), null, "CUSTOMER");
        mockMvc.perform(post("/api/v1/bookings")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CSRF_INVALID.getCode()));
        mockMvc.perform(post("/api/v1/bookings")
                        .cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", "csrf-value"))
                        .header("X-XSRF-TOKEN", "csrf-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isOk());
    }

    // ===== Kiểm khi tạo / duyệt booking (BK-02) + lịch bận cho Provider (C5.1) =====

    @Test
    void bookingChecksDebtPendingLimitAndCaregiverAvailability() throws Exception {
        Scenario scenario = newScenario();
        // Khách đang nợ → không đặt được; Payment sập → 503 (fail-closed).
        PLATFORM.addDebtor(scenario.customerId());
        createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CUSTOMER_HAS_OUTSTANDING_DEBT.getCode()));
        Scenario other = newScenario();
        PLATFORM.setPaymentFailing(true);
        createBooking(other, weeklyBody(other, null, 0), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE.getCode()));
        PLATFORM.setPaymentFailing(false);

        // Caregiver không có lịch rảnh → 409 khi khách chọn lẫn khi trung tâm phân công.
        CaregiverIds busy = PLATFORM.addActiveCaregiver(other.centerId());
        PLATFORM.setCaregiverUnavailable(busy.caregiverId());
        createBooking(other, weeklyBody(other, busy.caregiverId(), 0), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_AVAILABLE.getCode()));
        String bookingId = createAndGetId(other, weeklyBody(other, null, 0));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiverId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_AVAILABLE.getCode()));

        // Giới hạn 3 booking PENDING / AWAITING_DEPOSIT mỗi khách.
        createAndGetId(other, weeklyBody(other, null, 2));
        createAndGetId(other, weeklyBody(other, null, 4));
        createBooking(other, weeklyBody(other, null, 6), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_PENDING_BOOKINGS.getCode()));
    }

    @Test
    void busyCaregiversReportsOnlyOverlappingActiveOccurrences() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        BookingOccurrence first = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst();
        CaregiverIds free = PLATFORM.addActiveCaregiver(scenario.centerId());
        Map<String, Object> body = Map.of(
                "caregiverIds", List.of(scenario.caregiver().caregiverId().toString(), free.caregiverId().toString()),
                "slots", List.of(Map.of("startAt", first.getStartAt().plusSeconds(1800).toString(),
                        "endAt", first.getEndAt().plusSeconds(1800).toString())));
        mockMvc.perform(post("/api/v1/booking-occurrences/busy-caregivers").header("Authorization", serviceBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.busyCaregiverIds.length()").value(1))
                .andExpect(jsonPath("$.result.busyCaregiverIds[0]").value(scenario.caregiver().caregiverId().toString()));
        // Khoảng bắt đầu đúng lúc buổi kết thúc không tính là bận (nửa mở).
        Map<String, Object> adjacent = Map.of(
                "caregiverIds", List.of(scenario.caregiver().caregiverId().toString()),
                "slots", List.of(Map.of("startAt", first.getEndAt().toString(),
                        "endAt", first.getEndAt().plusSeconds(3600).toString())));
        mockMvc.perform(post("/api/v1/booking-occurrences/busy-caregivers").header("Authorization", serviceBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(adjacent)))
                .andExpect(jsonPath("$.result.busyCaregiverIds.length()").value(0));
        mockMvc.perform(post("/api/v1/booking-occurrences/busy-caregivers").header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isForbidden());
    }

    // ===== Giá từng buổi + báo giá (BK-01) =====

    @Test
    void quotePricesEachOccurrenceAndWritesNothing() throws Exception {
        Scenario scenario = newScenario();
        UUID dailyPlan = UUID.randomUUID();
        UUID dailyService = UUID.randomUUID();
        PLATFORM.addPricePlan(scenario.pricePlanId(), "HOUR", 60, 100000);
        PLATFORM.addPricePlan(dailyPlan, "DAY", 480, 300000);
        Map<String, Object> body = threeDaysOfTwoAndHalfHours(scenario, dailyService, dailyPlan);
        long bookingsBefore = bookingRepository.count();

        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrenceCount").value(3))
                .andExpect(jsonPath("$.result.occurrences[0].priceAmount").value(600000))
                .andExpect(jsonPath("$.result.lines[0].unitsPerOccurrence").value(3))
                .andExpect(jsonPath("$.result.lines[1].unitsPerOccurrence").value(1))
                .andExpect(jsonPath("$.result.totalAmount").value(1800000))
                .andExpect(jsonPath("$.result.depositPercent").value(30))
                .andExpect(jsonPath("$.result.estimatedDepositAmount").value(540000));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);

        // PaymentService chưa sẵn sàng: vẫn báo giá, chỉ thiếu phần cọc.
        PLATFORM.setPaymentFailing(true);
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAmount").value(1800000))
                .andExpect(jsonPath("$.result.depositPercent").doesNotExist());
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createStoresOccurrencePricesAndPaymentContextExposesThem() throws Exception {
        Scenario scenario = newScenario();
        UUID dailyPlan = UUID.randomUUID();
        PLATFORM.addPricePlan(scenario.pricePlanId(), "HOUR", 60, 100000);
        PLATFORM.addPricePlan(dailyPlan, "DAY", 480, 300000);
        String bookingId = createAndGetId(scenario, threeDaysOfTwoAndHalfHours(scenario, UUID.randomUUID(), dailyPlan));

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAmount").value(1800000))
                .andExpect(jsonPath("$.result.depositAmount").doesNotExist())
                .andExpect(jsonPath("$.result.occurrences[2].priceAmount").value(600000))
                .andExpect(jsonPath("$.result.services[0].unitsPerOccurrence").exists());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payment-context").header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingId").value(bookingId))
                .andExpect(jsonPath("$.result.customerId").value(scenario.customerId().toString()))
                .andExpect(jsonPath("$.result.totalAmount").value(1800000))
                .andExpect(jsonPath("$.result.firstStartAt").exists())
                .andExpect(jsonPath("$.result.occurrences.length()").value(3))
                .andExpect(jsonPath("$.result.occurrences[0].priceAmount").value(600000))
                .andExpect(jsonPath("$.result.occurrences[0].hasSession").value(false));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payment-context").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pricingRulesAndCatalogOutage() throws Exception {
        Scenario scenario = newScenario();
        Map<String, Object> missingPlan = weeklyBody(scenario, null, 0);
        missingPlan.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString())));
        createBooking(scenario, missingPlan, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRICE_PLAN_REQUIRED.getCode()));

        // Gói theo ca 4 giờ nhưng buổi 5 giờ → 400.
        PLATFORM.addPricePlan(scenario.pricePlanId(), "SHIFT", 240, 500000);
        Map<String, Object> longShift = weeklyBody(scenario, null, 0);
        longShift.put("startTime", "07:00");
        longShift.put("endTime", "12:00");
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(longShift)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRICE_PLAN_UNIT_MISMATCH.getCode()));

        long bookingsBefore = bookingRepository.count();
        PLATFORM.setCatalogFailing(true);
        createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.CARE_CATALOG_UNAVAILABLE.getCode()));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
    }

    // ===== Caregiver nhận ca + chi tiết ca (OPS-01) =====

    @Test
    void caregiverAcknowledgesAssignedOccurrenceAndReassignmentResetsIt() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String occurrenceId = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get("items").get(0).get("id").asString();

        // Trung tâm đã chấp nhận nhưng khách chưa cọc → chưa nhận ca được.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_NOT_CONFIRMED.getCode()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isOk());

        CaregiverIds stranger = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge")
                        .header("Authorization", bearer(stranger.userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge").header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.result.acknowledgedAt").exists());
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_OCCURRENCE_TRANSITION.getCode()));
        assertThat(bookingEventRepository.findAllByBooking(UUID.fromString(bookingId)))
                .extracting(event -> event.getEventType().name())
                .containsExactly("OCCURRENCE_ACKNOWLEDGED");

        // Chi tiết ca: dịch vụ, ghi chú đặc biệt, SĐT khách bị che vì buổi còn > 24h.
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.services[0].careServiceId").value(scenario.careServiceId().toString()))
                .andExpect(jsonPath("$.result.services[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.result.specialNotes.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.result.customerPhone").value("09******21"));
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.customerPhone").value("0987654321"));

        // Đổi người cho buổi đã nhận → quay về SCHEDULED, người mới phải nhận lại.
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"));
        mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/acknowledge")
                        .header("Authorization", bearer(replacement.userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isOk());
    }

    // ===== Ca chăm sóc: check-in / checklist / check-out / FINALIZE (OPS-02, OPS-04) =====

    @Test
    void careSessionFlowFinalizesBookingAndQueuesRemainingInvoiceOnce() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        UUID second = occurrences.get(1).getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");

        // Còn > 30 phút mới tới giờ → chưa check-in được.
        startSession(caregiverBearer, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_WINDOW_NOT_OPEN.getCode()));
        moveIntoCheckInWindow(first);
        boolean medicationInWindow = addMedicationDueIn(bookingId, 30);
        startSession(bearer(PLATFORM.addActiveCaregiver(scenario.centerId()).userId(), scenario.centerId(), "CAREGIVER"), first)
                .andExpect(status().isNotFound());
        MvcResult started = startSession(caregiverBearer, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.tasks[0].taskType").value("SERVICE"))
                .andReturn();
        String sessionId = readResult(started).get("sessionId").asString();
        assertThat(readResult(started).get("tasks").size()).isEqualTo(medicationInWindow ? 2 : 1);
        startSession(caregiverBearer, first)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_ALREADY_EXISTS.getCode()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.startedAt").exists())
                .andExpect(jsonPath("$.result.occurrences[0].status").value("IN_PROGRESS"));
        mockMvc.perform(get("/api/v1/booking-occurrences/" + first).header("Authorization", caregiverBearer))
                .andExpect(jsonPath("$.result.sessionId").value(sessionId));
        mockMvc.perform(get("/api/v1/care-sessions/" + sessionId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-sessions/" + sessionId).header("Authorization", customerBearer(newScenario())))
                .andExpect(status().isNotFound());

        // Check-out cần mọi việc DONE / SKIPPED.
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.TASKS_PENDING.getCode()));
        markAllTasksDone(UUID.fromString(sessionId));
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end").header("Authorization", caregiverBearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":10.77,\"longitude\":106.70}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ENDED"));
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_IN_PROGRESS.getCode()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payment-context").header("Authorization", serviceBearer()))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.occurrences[0].hasSession").value(true))
                .andExpect(jsonPath("$.result.occurrences[1].hasSession").value(false));

        // Buổi 2: caregiver quên kết thúc → manager buộc kết thúc; buổi cuối xong → booking COMPLETED, hóa đơn REMAINING 1 lần.
        moveIntoCheckInWindow(second);
        String secondSession = readResult(startSession(caregiverBearer, second).andExpect(status().isOk()).andReturn())
                .get("sessionId").asString();
        mockMvc.perform(post("/api/v1/care-sessions/" + secondSession + "/force-end")
                        .header("Authorization", bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Quên kết thúc"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/care-sessions/" + secondSession + "/force-end")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Caregiver quên kết thúc ca"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ENDED"))
                .andExpect(jsonPath("$.result.tasks[0].status").value("SKIPPED"));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.completedAt").exists());
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).hasSize(1);
        assertThat(bookingOutboxRepository.findAllByAggregateIdAndDeletedAtIsNull(UUID.fromString(bookingId)))
                .singleElement()
                .satisfies(outbox -> assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.SENT));
    }

    @Test
    void finalizeWithCancelledRemainderCompletesAndOutboxRetriesWhenPaymentIsDown() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        BookingOccurrence cancelled = occurrences.get(1);
        cancelled.setStatus(OccurrenceStatus.CANCELLED);
        bookingOccurrenceRepository.save(cancelled);
        moveIntoCheckInWindow(occurrences.get(0).getId());
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String sessionId = readResult(startSession(caregiverBearer, occurrences.get(0).getId())
                .andExpect(status().isOk()).andReturn()).get("sessionId").asString();
        markAllTasksDone(UUID.fromString(sessionId));

        PLATFORM.setPaymentFailing(true);
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end").header("Authorization", caregiverBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        BookingOutbox outbox = bookingOutboxRepository.findAllByAggregateIdAndDeletedAtIsNull(UUID.fromString(bookingId))
                .getFirst();
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getAttempts()).isEqualTo(1);
        assertThat(outbox.getLastError()).isNotBlank();

        // Payment bật lại → lượt quét sau gửi được, chỉ một yêu cầu thành công.
        PLATFORM.setPaymentFailing(false);
        outbox.setNextAttemptAt(Instant.now().minusSeconds(1));
        bookingOutboxRepository.save(outbox);
        bookingOutboxService.dispatch(outbox.getId());
        bookingOutboxService.dispatch(outbox.getId());
        assertThat(bookingOutboxRepository.findById(outbox.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).hasSize(1);
    }

    // ===== Báo khách vắng mặt + job MISSED (OPS-06) =====

    @Test
    void noShowReportDecisionAndMissedJobRefundOnceThenFinalize() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 3));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        UUID second = occurrences.get(1).getId();
        UUID third = occurrences.get(2).getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String note = "Đã gọi điện và chờ 30 phút, không ai mở cửa";

        // Chưa tới giờ bắt đầu → chưa báo vắng được; ghi chú quá ngắn → 400; caregiver khác → 404.
        reportNoShow(caregiverBearer, first, note)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_SHOW_WINDOW_INVALID.getCode()));
        // D22: quá mốc start + 30' (mốc MISSED) → hết hạn báo vắng.
        moveStartAgo(first, 40);
        reportNoShow(caregiverBearer, first, note)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_SHOW_WINDOW_INVALID.getCode()));
        moveStartAgo(first, 20);
        reportNoShow(caregiverBearer, first, "ngắn")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_SHOW_NOTE_REQUIRED.getCode()));
        reportNoShow(bearer(PLATFORM.addActiveCaregiver(scenario.centerId()).userId(), scenario.centerId(), "CAREGIVER"),
                first, note)
                .andExpect(status().isNotFound());
        reportNoShow(caregiverBearer, first, note)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.result.noShowReportedAt").exists());
        reportNoShow(caregiverBearer, first, note)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_SHOW_ALREADY_REPORTED.getCode()));

        // Job MISSED bỏ qua buổi đã báo vắng; buổi quá hạn check-in → MISSED, hoàn tiền đúng một lần.
        // Dời về khung giờ không chồng buổi 1 (cùng caregiver — exclusion constraint BK-09 chặn hai buổi trùng giờ).
        moveStartAgo(second, 170);
        assertThat(bookingOccurrenceService.findMissedIds()).contains(second).doesNotContain(first, third);
        bookingOccurrenceService.markMissed(second);
        bookingOccurrenceService.markMissed(second);
        BookingOccurrence missed = bookingOccurrenceRepository.findById(second).orElseThrow();
        assertThat(missed.getStatus()).isEqualTo(OccurrenceStatus.MISSED);
        assertThat(missed.getMissedReason()).isEqualTo(MissedReason.CAREGIVER);
        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CAREGIVER_MISSED:" + second)::equals).hasSize(1);

        // Manager: khác trung tâm → 404; buổi chưa báo vắng → 409; xác nhận → CUSTOMER_NO_SHOW; quyết định lại → 409.
        decideNoShow(bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER"), first, true)
                .andExpect(status().isNotFound());
        decideNoShow(managerBearer(scenario), second, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_SHOW_NOT_REPORTED.getCode()));
        decideNoShow(managerBearer(scenario), first, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CUSTOMER_NO_SHOW"));
        decideNoShow(managerBearer(scenario), first, false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_OCCURRENCE_TRANSITION.getCode()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        // Buổi cuối MISSED → không còn buổi nào chưa xong, có buổi khách vắng (tính phí) → COMPLETED + hóa đơn còn lại.
        moveStartAgo(third, 40);
        bookingOccurrenceService.markMissed(third);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).hasSize(1);
        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CAREGIVER_MISSED:" + third)::equals).hasSize(1);
    }

    @Test
    void rejectedNoShowReportRefundsAndCancelsBookingWithNothingServed() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveStartAgo(occurrenceId, 10);
        reportNoShow(caregiverBearer, occurrenceId, "Khách không có nhà, đã gọi ba lần").andExpect(status().isOk());

        // Trong 24h sau giờ kết thúc: chưa tới hạn tự quyết, manager còn quyết được.
        assertThat(bookingOccurrenceService.findUndecidedNoShowIds()).doesNotContain(occurrenceId);
        decideNoShow(managerBearer(scenario), occurrenceId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("MISSED"))
                .andExpect(jsonPath("$.result.missedReason").value("CAREGIVER"));
        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CAREGIVER_MISSED:" + occurrenceId)::equals).hasSize(1);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
        assertThat(PLATFORM.remainingInvoiceKeys()).noneMatch(("REMAINING_INVOICE:" + bookingId)::equals);
    }

    // D22 (M-08): quá start + 30' chưa check-in → không check-in được nữa, buổi thuộc diện MISSED.
    @Test
    void checkInClosesThirtyMinutesAfterTheVisitStarts() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveStartAgo(occurrenceId, 40);
        startSession(caregiverBearer, occurrenceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_WINDOW_NOT_OPEN.getCode()));
        assertThat(bookingOccurrenceService.findMissedIds()).contains(occurrenceId);
        moveStartAgo(occurrenceId, 25);
        startSession(caregiverBearer, occurrenceId).andExpect(status().isOk());
    }

    // D21 (H-10): báo vắng không ai quyết sau giờ kết thúc + 24h → hệ thống tự chấp nhận (khách chịu phí buổi).
    @Test
    void undecidedNoShowReportIsAutoAcceptedAfter24Hours() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveStartAgo(occurrenceId, 10);
        reportNoShow(caregiverBearer, occurrenceId, "Khách không có nhà, đã gọi ba lần").andExpect(status().isOk());

        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minus(Duration.ofHours(27)));
        occurrence.setEndAt(Instant.now().minus(Duration.ofHours(25)));
        bookingOccurrenceRepository.save(occurrence);
        assertThat(bookingOccurrenceService.findUndecidedNoShowIds()).contains(occurrenceId);
        bookingOccurrenceService.autoAcceptNoShow(occurrenceId);
        bookingOccurrenceService.autoAcceptNoShow(occurrenceId);

        BookingOccurrence decided = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(OccurrenceStatus.CUSTOMER_NO_SHOW);
        assertThat(decided.getNoShowDecidedAt()).isNotNull();
        assertThat(decided.getNoShowDecidedBy()).isNull();
        assertThat(bookingOccurrenceService.findUndecidedNoShowIds()).doesNotContain(occurrenceId);
        // Buổi duy nhất là khách vắng (tính phí) → booking COMPLETED + hóa đơn phần còn lại, manager không quyết lại được.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).hasSize(1);
        decideNoShow(managerBearer(scenario), occurrenceId, false).andExpect(status().isConflict());
    }

    // D21 (H-10): ca quên check-out quá giờ kết thúc + 4h → hệ thống tự đóng, buổi COMPLETED, cờ autoClosed.
    @Test
    void forgottenCareSessionIsAutoClosedFourHoursAfterTheVisitEnds() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveIntoCheckInWindow(occurrenceId);
        String sessionId = readResult(startSession(caregiverBearer, occurrenceId)
                .andExpect(status().isOk()).andReturn()).get("sessionId").asString();

        // Mới quá giờ kết thúc 3h → chưa tự đóng.
        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setEndAt(Instant.now().minus(Duration.ofHours(3)));
        bookingOccurrenceRepository.save(occurrence);
        assertThat(careSessionService.findAutoCloseIds()).doesNotContain(UUID.fromString(sessionId));

        occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setEndAt(Instant.now().minus(Duration.ofHours(5)));
        bookingOccurrenceRepository.save(occurrence);
        assertThat(careSessionService.findAutoCloseIds()).contains(UUID.fromString(sessionId));
        careSessionService.autoClose(UUID.fromString(sessionId));
        careSessionService.autoClose(UUID.fromString(sessionId));

        mockMvc.perform(get("/api/v1/care-sessions/" + sessionId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ENDED"))
                .andExpect(jsonPath("$.result.autoClosed").value(true));
        assertThat(bookingOccurrenceRepository.findById(occurrenceId).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.COMPLETED);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
    }

    ResultActions reportNoShow(String bearer, UUID occurrenceId, String note) throws Exception {
        return mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/no-show-reports")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("note", note))));
    }

    ResultActions decideNoShow(String bearer, UUID occurrenceId, boolean approved) throws Exception {
        return mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/no-show-reports/decision")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("approved", approved))));
    }

    // Dời buổi về "đã bắt đầu minutes phút trước", dài 2 giờ.
    void moveStartAgo(UUID occurrenceId, long minutes) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minus(Duration.ofMinutes(minutes)));
        occurrence.setEndAt(occurrence.getStartAt().plus(Duration.ofHours(2)));
        bookingOccurrenceRepository.save(occurrence);
    }


    String createRunningAftercareSession(Scenario scenario) throws Exception {
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        moveIntoCheckInWindow(occurrenceId);
        return readResult(startSession(bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"), occurrenceId)
                .andExpect(status().isOk()).andReturn()).get("sessionId").asText();
    }

    void endAftercareSession(Scenario scenario, String sessionId) throws Exception {
        markAllTasksDone(UUID.fromString(sessionId));
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end")
                .header("Authorization", bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isOk());
    }

    @Test
    void notificationsArePrivateAndRollbackDoesNotPublish() throws Exception {
        UUID customer = UUID.randomUUID();
        String owner = bearer(customer, null, "CUSTOMER");
        String body = json(Map.of("recipientUserIds", List.of(customer), "type", "REPORT_SUBMITTED", "title", "Báo cáo mới",
                "body", "Có báo cáo chăm sóc", "refType", "CARE_REPORT", "refId", UUID.randomUUID()));
        mockMvc.perform(post("/api/v1/notifications").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notifications").header("Authorization", serviceBearer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.created").value(1));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", owner))
                .andExpect(jsonPath("$.result.count").value(1));
        JsonNode list = readResult(mockMvc.perform(get("/api/v1/notifications").header("Authorization", owner)).andReturn());
        String id = list.path("items").get(0).path("id").asText();
        mockMvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", bearer(UUID.randomUUID(), null, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", owner)).andExpect(status().isOk());
        transactionTemplate.executeWithoutResult(status -> {
            notificationService.notify(List.of(customer), NotificationType.REPORT_SUBMITTED, Map.of(), "CARE_REPORT", UUID.randomUUID());
            status.setRollbackOnly();
        });
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", owner))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void sessionTasksNotesAndReportsEnforceLifecycleAndScope() throws Exception {
        Scenario scenario = newScenario();
        String sessionId = createRunningAftercareSession(scenario);
        String caregiver = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String taskId = careTaskRepository.findAllBySession(UUID.fromString(sessionId)).getFirst().getId().toString();
        mockMvc.perform(patch("/api/v1/care-sessions/" + sessionId + "/tasks/" + taskId).header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "SKIPPED"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/care-sessions/" + sessionId + "/tasks/" + taskId).header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "SKIPPED", "skipReason", "Người bệnh đang ngủ"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/care-sessions/" + sessionId + "/tasks/" + taskId).header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.skipReason").doesNotExist());
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/notes").header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "Ổn định", "vitals", Map.of("temperature", 50)))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/notes").header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("content", "Ổn định", "vitals", Map.of("temperature", 37, "spo2", 98)))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-sessions/" + sessionId + "/notes").header("Authorization", customerBearer(newScenario())))
                .andExpect(status().isNotFound());
        Map<String, Object> report = new LinkedHashMap<>(Map.of("sessionId", sessionId, "overallCondition", "STABLE", "summary", "Buổi chăm sóc ổn định"));
        String reportId = readResult(mockMvc.perform(post("/api/v1/care-reports").header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(report))).andExpect(status().isOk()).andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/care-reports").header("Authorization", caregiver).contentType(MediaType.APPLICATION_JSON).content(json(report)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/care-reports/" + reportId + "/submit").header("Authorization", caregiver)).andExpect(status().isConflict());
        // Bản nháp chỉ caregiver viết nó thấy (M-01).
        mockMvc.perform(get("/api/v1/care-reports/" + reportId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/care-reports").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items").isEmpty());
        mockMvc.perform(get("/api/v1/care-reports/" + reportId).header("Authorization", caregiver))
                .andExpect(status().isOk());
        endAftercareSession(scenario, sessionId);
        mockMvc.perform(post("/api/v1/care-reports/" + reportId + "/submit").header("Authorization", caregiver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("SUBMITTED"));
        report.put("version", 1);
        mockMvc.perform(put("/api/v1/care-reports/" + reportId).header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(report))).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/care-reports/" + reportId).header("Authorization", customerBearer(newScenario())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/care-reports/" + reportId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-reports").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.items.length()").value(1));
    }

    @Test
    void reportPhotoLimitCompensatesUploadedFile() throws Exception {
        Scenario scenario = newScenario();
        String session = createRunningAftercareSession(scenario);
        String caregiver = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String id = readResult(mockMvc.perform(post("/api/v1/care-reports").header("Authorization", caregiver)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("sessionId", session, "overallCondition", "STABLE", "summary", "Báo cáo"))))
                .andExpect(status().isOk()).andReturn()).path("id").asText();
        List<UUID> uploaded = new ArrayList<>();
        when(mediaClient.upload(any(), eq("CARE_REPORT_IMAGE"), any(), eq("CARE_REPORT"), any())).thenAnswer(invocation -> {
            UUID mediaId = UUID.randomUUID();
            uploaded.add(mediaId);
            return APIResponse.success("ok", MediaClientResponse.builder().mediaFileId(mediaId).build());
        });
        when(mediaClient.createDownloadUrl(any())).thenAnswer(invocation -> APIResponse.success("ok",
                DownloadUrlClientResponse.builder().mediaFileId(invocation.getArgument(0)).downloadUrl("https://media.test/image")
                        .expiresAt(Instant.now().plusSeconds(300)).build()));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(multipart("/api/v1/care-reports/" + id + "/photos")
                    .file(new MockMultipartFile("files", "photo.png", "image/png", new byte[]{1, 2, 3}))
                    .header("Authorization", caregiver)).andExpect(status().isOk());
        }
        mockMvc.perform(multipart("/api/v1/care-reports/" + id + "/photos")
                .file(new MockMultipartFile("files", "six.png", "image/png", new byte[]{1}))
                .header("Authorization", caregiver)).andExpect(status().isBadRequest());
        verify(mediaClient).delete(uploaded.get(5));
    }

    @Test
    void reviewsAreUniqueModeratedAndScoped() throws Exception {
        Scenario scenario = newScenario();
        String session = createRunningAftercareSession(scenario);
        endAftercareSession(scenario, session);
        UUID bookingId = bookingOccurrenceRepository.findAll().stream().filter(o -> scenario.caregiver().caregiverId().equals(o.getCaregiverId()))
                .findFirst().orElseThrow().getBooking().getId();
        String request = json(Map.of("bookingId", bookingId, "caregiverId", scenario.caregiver().caregiverId(), "rating", 5, "comment", "Tận tâm"));
        String id = readResult(mockMvc.perform(post("/api/v1/feedbacks").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/feedbacks").header("Authorization", customerBearer(scenario)).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/feedbacks/" + id + "/reply").header("Authorization", managerBearer(newScenario()))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "Cảm ơn")))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/feedbacks/" + id + "/reply").header("Authorization", managerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "Cảm ơn")))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/feedbacks/" + id + "/reply").header("Authorization", managerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reply", "Lần hai")))).andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/feedbacks/" + id + "/visibility").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("visible", false, "reason", "Ẩn kiểm thử")))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/feedbacks").param("caregiverId", scenario.caregiver().caregiverId().toString()).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void complaintsEscalateAndRefundOnceWithoutChangingStateOnOverRefund() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        // D20: chỉ khiếu nại buổi đã diễn ra.
        moveStartAgo(bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId(), 10);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("bookingId", bookingId, "targetType", "CAREGIVER_SERVICE",
                "category", "QUALITY", "title", "Chất lượng", "content", "Cần xem lại buổi chăm sóc"));
        String id = readResult(mockMvc.perform(post("/api/v1/complaints").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isOk()).andReturn()).path("id").asText();
        mockMvc.perform(patch("/api/v1/complaints/" + id + "/accept").header("Authorization", managerBearer(scenario))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/complaints/" + id + "/resolve").header("Authorization", managerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("resolution", "Đã trao đổi")))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/complaints/" + id + "/escalate").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Chưa đồng ý")))).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/complaints/" + id + "/admin-resolve").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("resolution", "Hoàn tiền", "refundAmount", 1000001))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/complaints/" + id).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("ESCALATED"));
        String resolve = json(Map.of("resolution", "Hoàn một phần", "refundAmount", 200000));
        mockMvc.perform(patch("/api/v1/complaints/" + id + "/admin-resolve").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content(resolve)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RESOLVED_BY_ADMIN")).andExpect(jsonPath("$.result.events.length()").value(5));
        mockMvc.perform(patch("/api/v1/complaints/" + id + "/admin-resolve").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content(resolve)).andExpect(status().isConflict());
        assertThat(PLATFORM.refundKeys().stream().filter(key -> key.equals("COMPLAINT_REFUND:" + id)).count()).isEqualTo(1);
        body.put("targetType", "CENTER");
        String centerComplaint = readResult(mockMvc.perform(post("/api/v1/complaints").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isOk()).andReturn()).path("id").asText();
        mockMvc.perform(patch("/api/v1/complaints/" + centerComplaint + "/accept").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict());
    }

    // D20 (H-09): hạn 7 ngày, một khiếu nại đang mở mỗi buổi, rút khiếu nại, khách chuyển admin sau 48h.
    @Test
    void complaintRulesFollowTheWindowOneOpenPerVisitWithdrawAndEscalation() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        Map<String, Object> body = new LinkedHashMap<>(Map.of("bookingId", bookingId, "occurrenceId", occurrenceId.toString(),
                "targetType", "CAREGIVER_SERVICE", "category", "LATE", "title", "Đến muộn", "content", "Đến muộn 20 phút"));

        // Buổi chưa diễn ra → không khiếu nại được (cả cấp buổi lẫn cấp booking).
        complain(scenario, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5926));
        complain(scenario, Map.of("bookingId", bookingId, "targetType", "CAREGIVER_SERVICE", "category", "LATE",
                "title", "Đến muộn", "content", "Đến muộn")).andExpect(jsonPath("$.code").value(5926));

        moveStartAgo(occurrenceId, 10);
        String first = readResult(complain(scenario, body).andExpect(status().isOk()).andReturn()).path("id").asText();
        complain(scenario, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5927));

        // Khách chưa được vượt cấp khi trung tâm còn trong 48h; quá 48h thì được.
        escalateAsCustomer(scenario, first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5928));
        jdbcTemplate.update("update complaints set created_at = ? where complaint_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(49))), UUID.fromString(first));
        escalateAsCustomer(scenario, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ESCALATED")).andExpect(jsonPath("$.result.level").value("ADMIN"));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(ADMIN_USER_ID, null, "ADMIN"))
                        .param("size", "100"))
                .andExpect(jsonPath("$.result.items[?(@.refId == '" + first + "')]").isNotEmpty());

        // Rút khiếu nại chưa giải quyết → WITHDRAWN, buổi được mở khiếu nại mới; rút lần hai bị chặn.
        mockMvc.perform(post("/api/v1/complaints/" + first + "/withdraw").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("WITHDRAWN"));
        mockMvc.perform(post("/api/v1/complaints/" + first + "/withdraw").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isConflict());
        String second = readResult(complain(scenario, body).andExpect(status().isOk()).andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/complaints/" + second + "/withdraw").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isForbidden());

        // Quá 7 ngày sau giờ kết thúc buổi → hết hạn khiếu nại.
        mockMvc.perform(post("/api/v1/complaints/" + second + "/withdraw").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk());
        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minus(Duration.ofDays(8)));
        occurrence.setEndAt(Instant.now().minus(Duration.ofDays(8)).plus(Duration.ofHours(2)));
        bookingOccurrenceRepository.save(occurrence);
        complain(scenario, body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5926));
    }

    // M-09: trần hoàn khiếu nại trừ cả khoản hoàn Payment đang giữ chỗ.
    @Test
    void complaintRefundCapSubtractsRefundsStillPending() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        moveStartAgo(bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId(), 10);
        String id = readResult(complain(scenario, Map.of("bookingId", bookingId, "targetType", "CENTER", "category", "BILLING",
                "title", "Thu sai", "content", "Trung tâm thu sai")).andExpect(status().isOk()).andReturn()).path("id").asText();
        PLATFORM.setReservedRefundAmount(900000);
        try {
            mockMvc.perform(patch("/api/v1/complaints/" + id + "/admin-resolve").header("Authorization", adminBearer())
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("resolution", "Hoàn", "refundAmount", 200000))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5923));
            mockMvc.perform(patch("/api/v1/complaints/" + id + "/admin-resolve").header("Authorization", adminBearer())
                    .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("resolution", "Hoàn", "refundAmount", 100000))))
                    .andExpect(status().isOk());
        } finally {
            PLATFORM.setReservedRefundAmount(0);
        }
    }

    // H-08: admin xem / xếp lại / bỏ qua việc outbox FAILED; hết lượt retry thì admin được báo.
    @Test
    void adminRequeuesAndSkipsFailedOutboxEvents() throws Exception {
        String key = "REFUND:TEST:" + UUID.randomUUID();
        bookingOutboxService.enqueue(OutboxEventType.REFUND, UUID.randomUUID(), key, Map.of());
        UUID id = bookingOutboxRepository.findAll().stream().filter(row -> key.equals(row.getIdempotencyKey()))
                .findFirst().orElseThrow().getId();
        String admin = bearer(ADMIN_USER_ID, null, "ADMIN");
        PLATFORM.setPaymentFailing(true);
        try {
            // Lượt cuối cùng còn được thử (một lỗi duy nhất, không làm mở circuit breaker của các test khác).
            BookingOutbox row = bookingOutboxRepository.findById(id).orElseThrow();
            row.setAttempts(bookingProperties.getOutbox().getMaxAttempts() - 1);
            row.setNextAttemptAt(Instant.now().minusSeconds(1));
            bookingOutboxRepository.save(row);
            bookingOutboxService.dispatch(id);
        } finally {
            PLATFORM.setPaymentFailing(false);
        }
        assertThat(bookingOutboxRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.FAILED);
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", admin).param("size", "100"))
                .andExpect(jsonPath("$.result.items[?(@.refId == '" + id + "')].type").value(org.hamcrest.Matchers.hasItem("OUTBOX_FAILED")));

        mockMvc.perform(get("/api/v1/booking-outbox").param("status", "FAILED").param("size", "100")
                        .header("Authorization", bearer(UUID.randomUUID(), UUID.randomUUID(), "CENTER_MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/booking-outbox").param("status", "FAILED").param("size", "100").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[?(@.id == '" + id + "')].lastError").isNotEmpty());

        mockMvc.perform(post("/api/v1/booking-outbox/" + id + "/requeue").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PENDING"))
                .andExpect(jsonPath("$.result.attempts").value(0));
        mockMvc.perform(post("/api/v1/booking-outbox/" + id + "/requeue").header("Authorization", admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(5961));
        bookingOutboxService.dispatch(id);
        assertThat(bookingOutboxRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(PLATFORM.refundKeys()).contains(key);

        BookingOutbox failed = bookingOutboxRepository.findById(id).orElseThrow();
        failed.setStatus(OutboxStatus.FAILED);
        bookingOutboxRepository.save(failed);
        mockMvc.perform(post("/api/v1/booking-outbox/" + id + "/skip").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("note", "Đã hoàn tay trên VNPay"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("SKIPPED"))
                .andExpect(jsonPath("$.result.lastError").value("Admin xử lý tay: Đã hoàn tay trên VNPay"));
        mockMvc.perform(post("/api/v1/booking-outbox/" + UUID.randomUUID() + "/requeue").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    ResultActions complain(Scenario scenario, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/v1/complaints").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    ResultActions escalateAsCustomer(Scenario scenario, String complaintId) throws Exception {
        return mockMvc.perform(post("/api/v1/complaints/" + complaintId + "/escalate").header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Trung tâm chưa xử lý"))));
    }

    @Test
    void operationalStatisticsForceManagerScope() throws Exception {
        Scenario scenario = newScenario();
        String session = createRunningAftercareSession(scenario);
        endAftercareSession(scenario, session);
        mockMvc.perform(get("/api/v1/statistics/centers").header("Authorization", managerBearer(scenario))
                .param("centerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.occurrences.completed").value(1))
                .andExpect(jsonPath("$.result.completionRate").value(100.0));
        mockMvc.perform(get("/api/v1/statistics/system").header("Authorization", managerBearer(scenario))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/statistics/system").header("Authorization", adminBearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.byCenter.totalElements").isNumber());
        mockMvc.perform(get("/api/v1/statistics/centers").header("Authorization", managerBearer(scenario))
                .param("from", "2020-01-01").param("to", "2022-01-01")).andExpect(status().isBadRequest());
    }

    ResultActions startSession(String bearer, UUID occurrenceId) throws Exception {
        return mockMvc.perform(post("/api/v1/care-sessions")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("occurrenceId", occurrenceId.toString()))));
    }

    // Dời buổi về "đang diễn ra" (bắt đầu 10 phút trước, dài 2 giờ) — lịch đặt luôn cách hiện tại ≥ 12h.
    void moveIntoCheckInWindow(UUID occurrenceId) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minusSeconds(600));
        occurrence.setEndAt(Instant.now().plusSeconds(6600));
        bookingOccurrenceRepository.save(occurrence);
    }

    // Thuốc uống sau minutes phút (giờ VN) trong snapshot; false nếu khung giờ ca vắt qua nửa đêm (không kiểm task thuốc).
    boolean addMedicationDueIn(String bookingId, int minutes) {
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        LocalTime now = LocalTime.now(ZONE);
        LocalTime doseTime = now.plusMinutes(minutes);
        if (doseTime.isBefore(now) || now.minusMinutes(10).isAfter(now) || now.plusMinutes(110).isBefore(now)) {
            return false;
        }
        booking.getRecipientSnapshot().setActiveMedications(List.of(RecipientSnapshot.MedicationItem.builder()
                .medicationName("Amlodipine")
                .dosage("5mg")
                .timeOfDay(List.of(doseTime.format(DateTimeFormatter.ofPattern("HH:mm"))))
                .build()));
        bookingRepository.save(booking);
        return true;
    }

    // Cập nhật việc trong ca là OPS-03 (người C) — test đặt thẳng DONE qua repository.
    void markAllTasksDone(UUID sessionId) {
        for (CareTask task : careTaskRepository.findAllBySession(sessionId)) {
            task.setStatus(CareTaskStatus.DONE);
            careTaskRepository.save(task);
        }
    }

    Map<String, Object> dailyBody(Scenario scenario, UUID caregiverId, int days) {
        LocalDate start = LocalDate.now(ZONE).plusDays(10);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientId", scenario.recipientId().toString());
        body.put("centerId", scenario.centerId().toString());
        body.put("caregiverId", caregiverId.toString());
        body.put("scheduleType", "DAILY");
        body.put("startDate", start.toString());
        body.put("endDate", start.plusDays(days - 1L).toString());
        body.put("startTime", "08:00");
        body.put("endTime", "10:00");
        body.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString(),
                "pricePlanId", scenario.pricePlanId().toString())));
        return body;
    }

    // Hằng ngày, 3 ngày, 08:00–10:30 (150 phút); dịch vụ theo giờ của scenario + một dịch vụ theo ngày.
    Map<String, Object> threeDaysOfTwoAndHalfHours(Scenario scenario, UUID dailyService, UUID dailyPlan) {
        LocalDate start = LocalDate.now(ZONE).plusDays(10);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientId", scenario.recipientId().toString());
        body.put("centerId", scenario.centerId().toString());
        body.put("scheduleType", "DAILY");
        body.put("startDate", start.toString());
        body.put("endDate", start.plusDays(2).toString());
        body.put("startTime", "08:00");
        body.put("endTime", "10:30");
        body.put("services", List.of(
                Map.of("careServiceId", scenario.careServiceId().toString(), "pricePlanId", scenario.pricePlanId().toString()),
                Map.of("careServiceId", dailyService.toString(), "pricePlanId", dailyPlan.toString())));
        return body;
    }

    @Test
    void ssePublishesOnlyCommittedEventsToTheirRecipient() throws Exception {
        UUID customer = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        MvcResult ownStream = mockMvc.perform(get("/api/v1/notifications/stream")
                        .header("Authorization", bearer(customer, null, "CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        MvcResult otherStream = mockMvc.perform(get("/api/v1/notifications/stream")
                        .header("Authorization", bearer(outsider, null, "CUSTOMER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(ownStream.getRequest().isAsyncStarted()).isTrue();
        assertThat(ownStream.getResponse().getContentAsString()).contains("event:heartbeat");
        transactionTemplate.executeWithoutResult(status -> {
            notificationService.notify(List.of(customer), NotificationType.REPORT_SUBMITTED, Map.of("body", "Rollback"), "CARE_REPORT", UUID.randomUUID());
            status.setRollbackOnly();
        });
        assertThat(ownStream.getResponse().getContentAsString()).doesNotContain("event:notification");
        notificationService.notify(List.of(customer), NotificationType.REPORT_SUBMITTED, Map.of("body", "Committed"), "CARE_REPORT", UUID.randomUUID());
        assertThat(ownStream.getResponse().getContentAsString()).contains("event:notification", "Committed");
        assertThat(otherStream.getResponse().getContentAsString()).doesNotContain("event:notification");
        notificationService.publishSessionUpdate(customer, Map.of("sessionId", UUID.randomUUID()));
        assertThat(ownStream.getResponse().getContentAsString()).contains("event:session-update");
        ownStream.getRequest().getAsyncContext().complete();
        otherStream.getRequest().getAsyncContext().complete();
    }

    @Test
    void shiftReminderIsSentOnceForAnUpcomingAssignedOccurrence() throws Exception {
        Scenario scenario = newScenario();
        String createdId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, createdId, null);
        UUID bookingId = UUID.fromString(createdId);
        BookingOccurrence occurrence = bookingOccurrenceRepository.findAllByBooking(bookingId).getFirst();
        occurrence.setStartAt(Instant.now().plus(Duration.ofHours(1)));
        occurrence.setEndAt(occurrence.getStartAt().plus(Duration.ofHours(2)));
        bookingOccurrenceRepository.save(occurrence);
        assertThat(bookingOccurrenceService.findShiftReminderIds()).contains(occurrence.getId());
        bookingOccurrenceService.remindShift(occurrence.getId());
        bookingOccurrenceService.remindShift(occurrence.getId());
        assertThat(bookingOccurrenceService.findShiftReminderIds()).doesNotContain(occurrence.getId());
        mockMvc.perform(get("/api/v1/notifications").param("type", "SHIFT_REMINDER")
                        .header("Authorization", bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(1));
    }

    // ===== helpers =====

    record Scenario(UUID customerId, UUID recipientId, UUID centerId, CaregiverIds caregiver, UUID careServiceId,
                    UUID pricePlanId) {
    }

    Scenario newScenario() {
        return newScenarioInCenter(PLATFORM.addCenter("ACTIVE"));
    }

    Scenario newScenarioInCenter(UUID centerId) {
        UUID customerId = PLATFORM.addUser("Khách Hàng", "0987654321");
        UUID recipientId = PLATFORM.addRecipient(customerId, "Bà Lan", "ACTIVE");
        CaregiverIds caregiver = PLATFORM.addActiveCaregiver(centerId);
        return new Scenario(customerId, recipientId, centerId, caregiver, TestPlatformServer.DEFAULT_CARE_SERVICE_ID,
                UUID.randomUUID());
    }

    // Thứ Hai / Tư / Sáu, 08:00–10:00, hai tuần bắt đầu từ Thứ Hai cách hôm nay ít nhất 10 ngày.
    Map<String, Object> weeklyBody(Scenario scenario, UUID caregiverId, int weekOffset) {
        LocalDate monday = LocalDate.now(ZONE).plusDays(10 + 7L * weekOffset).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientId", scenario.recipientId().toString());
        body.put("centerId", scenario.centerId().toString());
        if (caregiverId != null) {
            body.put("caregiverId", caregiverId.toString());
        }
        body.put("scheduleType", "WEEKLY");
        body.put("startDate", monday.toString());
        body.put("endDate", monday.plusDays(13).toString());
        body.put("startTime", "08:00");
        body.put("endTime", "10:00");
        body.put("daysOfWeek", new ArrayList<>(List.of(5, 1, 3)));
        body.put("customerNote", "Bà cần hỗ trợ đi lại");
        body.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString(),
                "pricePlanId", scenario.pricePlanId().toString())));
        return body;
    }

    ResultActions createBooking(Scenario scenario, Map<String, Object> body, String idempotencyKey) throws Exception {
        var request = post("/api/v1/bookings")
                .header("Authorization", customerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    String createAndGetId(Scenario scenario, Map<String, Object> body) throws Exception {
        MvcResult result = createBooking(scenario, body, null).andExpect(status().isOk()).andReturn();
        return readResult(result).get("id").asString();
    }

    void acceptAndConfirm(Scenario scenario, String bookingId, UUID caregiverId) throws Exception {
        String acceptBody = caregiverId == null ? "{}" : json(Map.of("caregiverId", caregiverId.toString()));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
    }

    void assertCaregiverAccess(UUID caregiverUserId, UUID recipientId, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v1/bookings/caregiver-access")
                        .param("caregiverUserId", caregiverUserId.toString())
                        .param("recipientId", recipientId.toString())
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(expected));
    }

    // Token client-credentials của service khác (IAM-01): roles=[SERVICE], sub = client_uuid.
    String serviceBearer() {
        return bearer(UUID.randomUUID(), null, "SERVICE");
    }

    String adminBearer() {
        return bearer(UUID.randomUUID(), null, "ADMIN");
    }

    String customerBearer(Scenario scenario) {
        return bearer(scenario.customerId(), null, "CUSTOMER");
    }

    String managerBearer(Scenario scenario) {
        return bearer(UUID.randomUUID(), scenario.centerId(), "CENTER_MANAGER");
    }

    String bearer(UUID userId, UUID centerId, String role) {
        return "Bearer " + PLATFORM.accessToken(userId, centerId, role);
    }

    String json(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    JsonNode readResult(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("result");
    }
}
