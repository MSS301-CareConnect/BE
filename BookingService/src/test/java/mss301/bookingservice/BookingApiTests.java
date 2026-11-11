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
import mss301.bookingservice.enums.SessionCompletionReview;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.CareSessionRepository;
import mss301.bookingservice.repository.BookingOutboxRepository;
import mss301.bookingservice.repository.CareTaskRepository;
import mss301.bookingservice.repository.NotificationRepository;
import mss301.bookingservice.service.BookingOccurrenceService;
import mss301.bookingservice.service.CaregiverAssignmentService;
import mss301.bookingservice.service.BookingOutboxService;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.service.BookingService;
import mss301.bookingservice.support.TestPlatformServer;
import mss301.bookingservice.support.TestPlatformServer.CaregiverIds;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.hamcrest.Matchers;
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
import static org.hamcrest.Matchers.hasSize;
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
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        registry.add("booking.schedule.hold-scan-interval", () -> "1h");
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
    CareSessionRepository careSessionRepository;

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
    CaregiverAssignmentService caregiverAssignmentService;

    @Autowired
    CareSessionService careSessionService;

    @Autowired
    mss301.bookingservice.config.BookingProperties bookingProperties;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    NotificationRepository notificationRepository;

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
                // 2026-11-09 (A): mọi booking chờ khoản đầu ngay, giữ chỗ 5 phút.
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("RESERVED"))
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
        // Spec §11.2: đã gắn caregiver nhưng chưa trả khoản đầu → buổi chỉ đang giữ lịch.
        assertThat(occurrences.get(0).get("status").asString()).isEqualTo("RESERVED");
        assertThat(occurrences.get(0).get("caregiverId").asString()).isEqualTo(scenario.caregiver().caregiverId().toString());
        assertThat(PLATFORM.lastCareRecipientRequestId()).isEqualTo(requestId);
        UUID bookingId = UUID.fromString(booking.get("id").asString());
        List<BookingStatusHistory> history = bookingStatusHistoryRepository.findAllByBooking(bookingId);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getFromStatus()).isNull();
        assertThat(history.getFirst().getToStatus()).isEqualTo(BookingStatus.AWAITING_INITIAL_PAYMENT);
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
        // Booking khác nối tiếp đúng giờ kết thúc (10:00): không trùng giờ nhưng thiếu 60 phút trừ hao di chuyển (2026-11-05)
        Map<String, Object> adjacent = weeklyBody(scenario, scenario.caregiver().caregiverId(), 0);
        adjacent.put("recipientId", otherRecipient.toString());
        adjacent.put("startTime", "10:00");
        adjacent.put("endTime", "12:00");
        createBooking(scenario, adjacent, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_TRAVEL_BUFFER_CONFLICT.getCode()));
        // Cách đủ 60 phút (11:00) thì được
        adjacent.put("startTime", "11:00");
        adjacent.put("endTime", "13:00");
        createBooking(scenario, adjacent, null).andExpect(status().isOk());
    }

    // ===== Luồng trung tâm → đặt cọc → caregiver =====

    @Test
    void fullFlowAcceptDepositThenCaregiverSeesScheduleAndRecipient() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        // Luồng cũ (booking trước 2026-11-09 chờ trung tâm xác nhận) vẫn chạy cho dữ liệu cũ.
        makeLegacyPending(bookingId, BookingStatus.PENDING_CENTER_CONFIRMATION);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        LocalDate from = LocalDate.now(ZONE);
        LocalDate to = from.plusDays(40);

        // PENDING: caregiver chưa được giao → không thấy gì; truy cập hồ sơ bị từ chối
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", scenario.caregiver().caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
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
                .containsExactly(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.AWAITING_INITIAL_PAYMENT,
                        BookingStatus.CONFIRMED);
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
        makeLegacyPending(bookingId, BookingStatus.PENDING_CAREGIVER_CONFIRMATION);
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
        // Booking cũ còn chờ trung tâm xác nhận → chưa nhận cọc.
        makeLegacyPending(bookingId, BookingStatus.PENDING_CENTER_CONFIRMATION);
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
                .andExpect(jsonPath("$.result.items[0].toStatus").value("AWAITING_INITIAL_PAYMENT"))
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
        // Khách kia hủy booking đang giữ chỗ → lịch được giải phóng.
        mockMvc.perform(post("/api/v1/bookings/" + busyBookingId + "/cancel")
                        .header("Authorization", customerBearer(busy))
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
        // Từ 2026-11-09 booking có caregiver được giữ chỗ và chờ khoản đầu → hết hạn theo depositDueAt.
        BookingStatus heldStatus = booking.getStatus();
        booking.setAcceptanceDueAt(Instant.now().minusSeconds(60));
        booking.setDepositDueAt(Instant.now().minus(Duration.ofMinutes(3)));
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
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(heldStatus);
        release.countDown();
        otherInstance.join(10_000);
        bookingExpiryService.expire(booking.getId());
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void overduePendingBookingExpiresAndReleasesSchedule() throws Exception {
        Scenario scenario = newScenario();
        // Không chọn caregiver: hệ thống giữ người rảnh trong 5 phút chờ cọc; quá hạn + grace thì nhả.
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(booking.getCaregiverId()).isEqualTo(scenario.caregiver().caregiverId());
        assertThat(booking.getDepositDueAt()).isAfter(Instant.now());
        booking.setDepositDueAt(Instant.now().minus(Duration.ofMinutes(3)));
        bookingRepository.save(booking);
        assertThat(bookingExpiryService.findExpirableIds()).contains(booking.getId());
        bookingExpiryService.expire(booking.getId());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("EXPIRED"));
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
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/caregiver")
                        .header("Authorization", managerBearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiverId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_AVAILABLE.getCode()));

        // Giới hạn 3 booking chưa trả khoản đầu mỗi khách.
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
                .andExpect(jsonPath("$.result.occurrences[0].priceAmount").value(550000))
                .andExpect(jsonPath("$.result.lines[0].unitsPerOccurrence").value(2.5))
                .andExpect(jsonPath("$.result.lines[0].quantity").value(7.5))
                .andExpect(jsonPath("$.result.lines[1].unitsPerOccurrence").value(1.0))
                .andExpect(jsonPath("$.result.totalAmount").value(1650000))
                .andExpect(jsonPath("$.result.depositPercent").value(30))
                .andExpect(jsonPath("$.result.estimatedDepositAmount").value(495000));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);

        // Tỷ lệ thanh toán đầu lấy từ center policy của Booking — PaymentService lỗi không ảnh hưởng báo giá.
        PLATFORM.setPaymentFailing(true);
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAmount").value(1650000))
                .andExpect(jsonPath("$.result.depositPercent").value(30));
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isForbidden());
    }

    // 2026-11-11: báo giá báo trùng lịch caregiver / caregiver nghỉ ngay, không đợi tới lúc gửi booking.
    @Test
    void quoteRejectsCaregiverConflictsAndTimeOff() throws Exception {
        Scenario scenario = newScenario();
        createBooking(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0), null)
                .andExpect(status().isOk());
        UUID otherRecipient = PLATFORM.addRecipient(scenario.customerId(), "Ông Nam", "ACTIVE");
        Map<String, Object> overlapping = weeklyBody(scenario, scenario.caregiver().caregiverId(), 0);
        overlapping.put("recipientId", otherRecipient.toString());
        overlapping.put("startTime", "09:00");
        overlapping.put("endTime", "11:00");
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(overlapping)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT.getCode()));

        CaregiverIds onLeave = PLATFORM.addActiveCaregiver(scenario.centerId());
        PLATFORM.setCaregiverUnavailable(onLeave.caregiverId());
        Map<String, Object> withLeave = weeklyBody(scenario, onLeave.caregiverId(), 2);
        withLeave.put("recipientId", otherRecipient.toString());
        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(withLeave)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_NOT_AVAILABLE.getCode()));
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
                .andExpect(jsonPath("$.result.totalAmount").value(1650000))
                .andExpect(jsonPath("$.result.depositAmount").doesNotExist())
                .andExpect(jsonPath("$.result.occurrences[2].priceAmount").value(550000))
                .andExpect(jsonPath("$.result.services[0].unitsPerOccurrence").exists());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payment-context").header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingId").value(bookingId))
                .andExpect(jsonPath("$.result.customerId").value(scenario.customerId().toString()))
                .andExpect(jsonPath("$.result.totalAmount").value(1650000))
                .andExpect(jsonPath("$.result.firstStartAt").exists())
                .andExpect(jsonPath("$.result.occurrences.length()").value(3))
                .andExpect(jsonPath("$.result.occurrences[0].priceAmount").value(550000))
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

        // Booking chờ cọc (giữ chỗ) → caregiver chưa nhận ca được.
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
                .containsOnlyOnce("OCCURRENCE_ACKNOWLEDGED");

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

    // 2026-11-11: quên bấm nhận trước giờ → còn nhận được tới khi đóng cửa sổ check-in (mặc định 30 phút sau giờ bắt đầu).
    @Test
    void caregiverStillAcknowledgesAStartedVisitUntilCheckInCloses() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isOk());
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID started = occurrences.get(0).getId();
        UUID closed = occurrences.get(1).getId();
        moveStartAgo(started, 10);
        moveStartAgo(closed, 31);

        mockMvc.perform(post("/api/v1/booking-occurrences/" + started + "/acknowledge").header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACKNOWLEDGED"));
        mockMvc.perform(post("/api/v1/booking-occurrences/" + closed + "/acknowledge").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_OCCURRENCE_TRANSITION.getCode()));
    }

    // ===== Ca chăm sóc: check-in / checklist / check-out / FINALIZE (OPS-02, OPS-04) =====

    @Test
    void careSessionFlowFinalizesBookingAndQueuesRemainingInvoiceOnce() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        // Booking cũ (trước spec v1, không có hạn phần còn lại): phần còn lại vẫn thu bằng hóa đơn sau hoàn tất.
        acceptAndPayInitial(scenario, bookingId, null, false);
        makeLegacyPayment(bookingId);
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
        acceptAndPayInitial(scenario, bookingId, null, false);
        makeLegacyPayment(bookingId);
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
        assertThat(missed.getStatus()).isEqualTo(OccurrenceStatus.CAREGIVER_MISSED);
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

        // Buổi cuối MISSED → không còn buổi nào chưa xong, có buổi khách vắng → COMPLETED. Đã trả đủ trước buổi đầu
        // (spec §9.1) nên không còn hóa đơn sau hoàn tất.
        moveStartAgo(third, 40);
        bookingOccurrenceService.markMissed(third);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).isEmpty();
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
                .andExpect(jsonPath("$.result.status").value("CAREGIVER_MISSED"))
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

    // Q12: cửa sổ check-in theo center policy, chụp lúc tạo booking — đổi policy sau không ảnh hưởng booking đã đặt.
    @Test
    void checkInWindowFollowsTheCenterPolicySnapshot() throws Exception {
        Scenario scenario = newScenario();
        Map<String, Object> policy = policyBody(30, null);
        policy.put("checkInEarlyMinutes", 60);
        policy.put("checkInLateMinutes", 10);
        mockMvc.perform(put("/api/v1/center-booking-policies/" + scenario.centerId())
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policy)))
                .andExpect(status().isOk());
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        // Đổi policy sau khi đặt: booking vẫn giữ 60 / 10.
        mockMvc.perform(put("/api/v1/center-booking-policies/" + scenario.centerId())
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policyBody(30, 0L))))
                .andExpect(status().isOk());
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");

        moveStartAgo(occurrenceId, 15);
        startSession(caregiverBearer, occurrenceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_WINDOW_NOT_OPEN.getCode()));
        assertThat(bookingOccurrenceService.findMissedIds()).contains(occurrenceId);
        mockMvc.perform(get("/api/v1/booking-occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.checkInClosesAt").exists())
                .andExpect(jsonPath("$.result.checkInOpensAt").exists());

        moveStartAgo(occurrenceId, -50);
        assertThat(bookingOccurrenceService.findMissedIds()).doesNotContain(occurrenceId);
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
        // Buổi duy nhất là khách vắng → booking COMPLETED, không hóa đơn sau hoàn tất (đã trả đủ trước, spec §9.1),
        // manager không quyết lại được.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        assertThat(PLATFORM.remainingInvoiceKeys()).filteredOn(("REMAINING_INVOICE:" + bookingId)::equals).isEmpty();
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

        // Mới quá giờ kết thúc 3h → chưa tự đóng. Dời cả giờ bắt đầu: PostgreSQL chặn end_at <= start_at
        // (ck_booking_occurrences_range).
        BookingOccurrence occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minus(Duration.ofHours(5)));
        occurrence.setEndAt(Instant.now().minus(Duration.ofHours(3)));
        bookingOccurrenceRepository.save(occurrence);
        assertThat(careSessionService.findAutoCloseIds()).doesNotContain(UUID.fromString(sessionId));

        occurrence = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        occurrence.setStartAt(Instant.now().minus(Duration.ofHours(7)));
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

        // 2026-11-11: ca tự đóng chờ trung tâm xác nhận — PaymentService chưa quyết toán buổi (settlementHeld).
        mockMvc.perform(get("/api/v1/care-sessions").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[?(@.sessionId == '" + sessionId + "')].completionReview")
                        .value("PENDING"));
        assertThat(careSessionRepository.findOccurrenceIdsByBookingAndReview(UUID.fromString(bookingId),
                SessionCompletionReview.PENDING)).containsExactly(occurrenceId);
        // Kết luận "không thực hiện" cần ghi chú; có ghi chú → buổi thành caregiver vắng, không kết luận lại được.
        String reviewPath = "/api/v1/care-sessions/" + sessionId + "/review";
        mockMvc.perform(post(reviewPath).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delivered\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(reviewPath).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"delivered\":false,\"note\":\"Khách xác nhận không ai đến\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.completionReview").value("NOT_DELIVERED"));
        assertThat(bookingOccurrenceRepository.findById(occurrenceId).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.CAREGIVER_MISSED);
        mockMvc.perform(post(reviewPath).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delivered\":true}"))
                .andExpect(status().isConflict());
    }

    // 2026-11-11: check-in ghi số phút trễ, gắn cờ khi không gửi vị trí, và chặn mở ca thứ hai khi ca trước chưa check-out.
    @Test
    void checkInRecordsAttendanceFlagsMissingLocationAndBlocksASecondRunningSession() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        UUID second = occurrences.get(1).getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveIntoCheckInWindow(first);
        BookingOccurrence late = bookingOccurrenceRepository.findById(first).orElseThrow();
        late.setStartAt(Instant.now().minus(Duration.ofMinutes(12)));
        // Kết thúc sớm để buổi thứ hai không trùng giờ caregiver (exclusion constraint).
        late.setEndAt(Instant.now().plus(Duration.ofMinutes(1)));
        bookingOccurrenceRepository.save(late);

        String sessionId = readResult(startSession(caregiverBearer, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.locationFlag").value("NO_LOCATION"))
                .andReturn()).get("sessionId").asString();
        assertThat(careSessionRepository.findById(UUID.fromString(sessionId)).orElseThrow().getLateMinutes())
                .isBetween(11, 13);

        BookingOccurrence next = bookingOccurrenceRepository.findById(second).orElseThrow();
        next.setStartAt(Instant.now().plus(Duration.ofMinutes(3)));
        next.setEndAt(Instant.now().plus(Duration.ofHours(2)));
        bookingOccurrenceRepository.save(next);
        startSession(caregiverBearer, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(5806));
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

    // 2026-11-11: khách báo nhanh — tới giờ chưa thấy người đến (chỉ báo trung tâm), hoặc buổi đã xong nhưng không đúng
    // (ca chờ trung tâm xác nhận, giữ quyết toán). Mỗi buổi một lần; khách khác → 404.
    @Test
    void customerReportsAVisitNobodyCameToAndDisputesAFinishedOneOnce() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String path = "/api/v1/booking-occurrences/" + occurrenceId + "/customer-reports";
        // Chưa tới giờ → không báo được.
        mockMvc.perform(post(path).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isConflict());
        moveStartAgo(occurrenceId, 5);
        mockMvc.perform(post(path).header("Authorization", customerBearer(newScenario())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(path).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.customerReportedAt").exists());
        mockMvc.perform(post(path).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(5824));

        Scenario finished = newScenario();
        String sessionId = createRunningAftercareSession(finished);
        endAftercareSession(finished, sessionId);
        UUID bookingOfSession = careSessionRepository.findWithBooking(UUID.fromString(sessionId)).orElseThrow()
                .getBooking().getId();
        UUID finishedOccurrence = bookingOccurrenceRepository.findAllByBooking(bookingOfSession).getFirst().getId();
        String disputePath = "/api/v1/booking-occurrences/" + finishedOccurrence + "/customer-reports";
        mockMvc.perform(post(disputePath).header("Authorization", customerBearer(finished))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"ngắn\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(disputePath).header("Authorization", customerBearer(finished))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Người chăm sóc về sau 10 phút\"}"))
                .andExpect(status().isOk());
        assertThat(careSessionRepository.findOccurrenceIdsByBookingAndReview(bookingOfSession,
                SessionCompletionReview.PENDING)).containsExactly(finishedOccurrence);
        mockMvc.perform(get("/api/v1/care-sessions").header("Authorization", managerBearer(finished)))
                .andExpect(jsonPath("$.result.items[0].customerReportNote").value("Người chăm sóc về sau 10 phút"));
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

    // Xóa / đánh dấu chưa đọc chỉ trên thông báo của chính mình; thông báo về buổi trả kèm booking để điều hướng.
    @Test
    void notificationsCanBeUnreadDeletedAndLinkToTheirBooking() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        UUID customer = UUID.randomUUID();
        String owner = bearer(customer, null, "CUSTOMER");
        String stranger = bearer(UUID.randomUUID(), null, "CUSTOMER");
        for (String refType : List.of("BOOKING_OCCURRENCE", "BOOKING", "CARE_REPORT")) {
            UUID refId = switch (refType) {
                case "BOOKING_OCCURRENCE" -> occurrenceId;
                case "BOOKING" -> UUID.fromString(bookingId);
                default -> UUID.randomUUID();
            };
            mockMvc.perform(post("/api/v1/notifications").header("Authorization", serviceBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("recipientUserIds", List.of(customer), "type", "OCCURRENCE_CANCELLED",
                                    "title", "Thông báo", "body", refType, "refType", refType, "refId", refId))))
                    .andExpect(status().isOk());
        }
        JsonNode items = readResult(mockMvc.perform(get("/api/v1/notifications").header("Authorization", owner)).andReturn())
                .path("items");
        Map<String, JsonNode> byRef = new HashMap<>();
        items.forEach(item -> byRef.put(item.path("refType").asText(), item));
        assertThat(byRef.get("BOOKING_OCCURRENCE").path("bookingId").asText()).isEqualTo(bookingId);
        assertThat(byRef.get("BOOKING_OCCURRENCE").path("occurrenceId").asText()).isEqualTo(occurrenceId.toString());
        assertThat(byRef.get("BOOKING").path("bookingId").asText()).isEqualTo(bookingId);
        // Báo cáo không còn tồn tại → không có đích, không lỗi.
        assertThat(byRef.get("CARE_REPORT").path("bookingId").isNull()).isTrue();

        String readId = byRef.get("BOOKING").path("id").asText();
        mockMvc.perform(patch("/api/v1/notifications/" + readId + "/read").header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/notifications/" + readId + "/unread").header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/" + readId + "/unread").header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", owner))
                .andExpect(jsonPath("$.result.count").value(3));
        // Chọn nhiều: đánh dấu đã đọc rồi chưa đọc; id của người khác không đổi.
        List<String> allIds = List.of(readId, byRef.get("BOOKING_OCCURRENCE").path("id").asText());
        mockMvc.perform(patch("/api/v1/notifications/read-state").header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", allIds, "read", true))))
                .andExpect(jsonPath("$.result.updated").value(0));
        mockMvc.perform(patch("/api/v1/notifications/read-state").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", allIds))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/notifications/read-state").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", allIds, "read", true))))
                .andExpect(jsonPath("$.result.updated").value(2));
        mockMvc.perform(patch("/api/v1/notifications/read-state").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", allIds, "read", false))))
                .andExpect(jsonPath("$.result.updated").value(2));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", owner))
                .andExpect(jsonPath("$.result.count").value(3));

        String deletedId = byRef.get("CARE_REPORT").path("id").asText();
        mockMvc.perform(delete("/api/v1/notifications/" + deletedId).header("Authorization", stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/notifications/" + deletedId).header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/notifications/" + deletedId).header("Authorization", owner))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/notifications/" + readId + "/read").header("Authorization", owner)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/notifications/read").header("Authorization", owner))
                .andExpect(jsonPath("$.result.deleted").value(1));
        String remainingId = byRef.get("BOOKING_OCCURRENCE").path("id").asText();
        mockMvc.perform(post("/api/v1/notifications/deletions").header("Authorization", stranger)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", List.of(remainingId)))))
                .andExpect(jsonPath("$.result.deleted").value(0));
        mockMvc.perform(post("/api/v1/notifications/deletions").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/notifications/deletions").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("ids", List.of(remainingId)))))
                .andExpect(jsonPath("$.result.deleted").value(1));
        mockMvc.perform(get("/api/v1/notifications").header("Authorization", owner))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    // A2: trung tâm thao tác trên booking đã quá hạn mà scheduler chưa chạy → booking đóng ngay, lỗi BOOKING_EXPIRED.
    @Test
    void actingOnOverdueBookingExpiresItImmediately() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        makeLegacyPending(bookingId, BookingStatus.PENDING_CENTER_CONFIRMATION);
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.setAcceptanceDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/reject").header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reason", "Không còn người"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(5116));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.expiredReason").value("CENTER_CONFIRMATION_TIMEOUT"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("EXPIRED"));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(5116));
    }

    // Hàng đợi trung tâm: đếm theo trạng thái, hạn gần nhất / sắp quá hạn; danh sách lọc nhiều trạng thái, sắp theo hạn.
    @Test
    void centerQueueCountsAndSortsByDeadline() throws Exception {
        Scenario scenario = newScenario();
        String later = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        String sooner = createAndGetId(scenario, weeklyBody(scenario, null, 2));
        makeLegacyPending(later, BookingStatus.PENDING_CENTER_CONFIRMATION);
        makeLegacyPending(sooner, BookingStatus.PENDING_CENTER_CONFIRMATION);
        Booking soonerBooking = bookingRepository.findById(UUID.fromString(sooner)).orElseThrow();
        soonerBooking.setAcceptanceDueAt(Instant.now().plus(Duration.ofMinutes(30)));
        bookingRepository.save(soonerBooking);
        String manager = managerBearer(scenario);

        JsonNode counts = readResult(mockMvc.perform(get("/api/v1/bookings/status-counts").header("Authorization", manager))
                .andExpect(status().isOk()).andReturn());
        JsonNode pending = null;
        for (JsonNode item : counts.path("statuses")) {
            if (item.path("status").asText().equals("PENDING_CENTER_CONFIRMATION")) {
                pending = item;
            }
        }
        assertThat(pending).isNotNull();
        assertThat(pending.path("count").asLong()).isEqualTo(2);
        assertThat(pending.path("dueSoonCount").asLong()).isEqualTo(1);
        // DB lưu tới micro giây (làm tròn) nên so sai lệch dưới 1 ms.
        assertThat(Duration.between(Instant.parse(pending.path("nearestDueAt").asText()), soonerBooking.getAcceptanceDueAt())
                .abs()).isLessThan(Duration.ofMillis(1));
        mockMvc.perform(get("/api/v1/bookings/status-counts").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/bookings").header("Authorization", manager)
                        .param("statuses", "PENDING_CENTER_CONFIRMATION", "CANCELLED").param("sort", "deadlineAt,asc"))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.items[0].id").value(sooner))
                .andExpect(jsonPath("$.result.items[0].deadlineType").value("CENTER_CONFIRMATION"))
                .andExpect(jsonPath("$.result.items[0].serviceNames[0]").isNotEmpty())
                .andExpect(jsonPath("$.result.items[1].id").value(later));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", manager).param("statuses", "CANCELLED"))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", manager).param("sort", "deadlineAt,sideways"))
                .andExpect(status().isBadRequest());
    }

    // Nhắc trung tâm mỗi mốc một lần; mốc đã qua lúc tạo booking không nhắc dồn.
    @Test
    void centerIsRemindedOncePerMarkBeforeConfirmationDeadline() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        makeLegacyPending(bookingId, BookingStatus.PENDING_CENTER_CONFIRMATION);
        UUID id = UUID.fromString(bookingId);
        assertThat(bookingExpiryService.findCenterReminderIds()).doesNotContain(id);
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setAcceptanceDueAt(Instant.now().plus(Duration.ofMinutes(90)));
        bookingRepository.save(booking);
        assertThat(bookingExpiryService.findCenterReminderIds()).contains(id);
        bookingExpiryService.remindCenterAction(id);
        bookingExpiryService.remindCenterAction(id);
        assertThat(bookingRepository.findById(id).orElseThrow().getCenterReminderMinutes()).isEqualTo(120);
        assertThat(notificationRepository.findAll()).filteredOn(notification -> id.equals(notification.getRefId())
                && notification.getType() == NotificationType.CENTER_ACTION_DUE).hasSize(1);
    }

    @Test
    void careTaskUndoClearsResolutionAndKeepsCheckoutBlockedUntilResolvedAgain() throws Exception {
        Scenario scenario = newScenario();
        String sessionId = createRunningAftercareSession(scenario);
        UUID sessionUuid = UUID.fromString(sessionId);
        String caregiver = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        markAllTasksDone(sessionUuid);
        CareTask task = careTaskRepository.findAllBySession(sessionUuid).getFirst();
        String taskPath = "/api/v1/care-sessions/" + sessionId + "/tasks/" + task.getId();

        for (CareTaskStatus resolvedStatus : List.of(CareTaskStatus.DONE, CareTaskStatus.SKIPPED)) {
            Map<String, Object> resolved = new LinkedHashMap<>(Map.of(
                    "status", resolvedStatus.name(), "note", "Ghi chú cần giữ sau hoàn tác"));
            if (resolvedStatus == CareTaskStatus.SKIPPED) {
                resolved.put("skipReason", "Người bệnh đang nghỉ");
            }
            mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                            .contentType(MediaType.APPLICATION_JSON).content(json(resolved)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value(resolvedStatus.name()));
            CareTask beforeUndo = careTaskRepository.findById(task.getId()).orElseThrow();
            if (resolvedStatus == CareTaskStatus.DONE) {
                assertThat(beforeUndo.getCompletedAt()).isNotNull();
            } else {
                assertThat(beforeUndo.getSkipReason()).isEqualTo("Người bệnh đang nghỉ");
            }

            mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("status", "PENDING", "skipReason", "Lý do cũ không được giữ"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("PENDING"))
                    .andExpect(jsonPath("$.result.completedAt").doesNotExist())
                    .andExpect(jsonPath("$.result.skipReason").doesNotExist())
                    .andExpect(jsonPath("$.result.note").value("Ghi chú cần giữ sau hoàn tác"));
            CareTask undone = careTaskRepository.findById(task.getId()).orElseThrow();
            assertThat(undone.getStatus()).isEqualTo(CareTaskStatus.PENDING);
            assertThat(undone.getCompletedAt()).isNull();
            assertThat(undone.getSkipReason()).isNull();
            assertThat(undone.getUpdatedBy()).isEqualTo(scenario.caregiver().userId());
            mockMvc.perform(get("/api/v1/care-sessions/" + sessionId).header("Authorization", caregiver))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.tasks[0].status").value("PENDING"));
            mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end")
                            .header("Authorization", caregiver))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TASKS_PENDING.getCode()));
            // Gửi lại trạng thái đích không được làm mất ghi chú hoặc đánh dấu công việc đã xong.
            mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                            .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PENDING"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("PENDING"))
                    .andExpect(jsonPath("$.result.note").value("Ghi chú cần giữ sau hoàn tác"));
        }

        mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/care-sessions/" + sessionId + "/end").header("Authorization", caregiver))
                .andExpect(status().isOk());
        mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "PENDING"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_IN_PROGRESS.getCode()));
        assertThat(careTaskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(CareTaskStatus.DONE);
    }

    @Test
    void careTaskUndoRequiresAssignedCaregiverAndTaskWithinThatSession() throws Exception {
        Scenario scenario = newScenario();
        String sessionId = createRunningAftercareSession(scenario);
        CareTask task = careTaskRepository.findAllBySession(UUID.fromString(sessionId)).getFirst();
        String caregiver = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        String taskPath = "/api/v1/care-sessions/" + sessionId + "/tasks/" + task.getId();
        mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DONE"))))
                .andExpect(status().isOk());
        String undo = json(Map.of("status", "PENDING"));
        mockMvc.perform(patch(taskPath).contentType(MediaType.APPLICATION_JSON).content(undo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch(taskPath).header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(undo))
                .andExpect(status().isForbidden());
        CaregiverIds otherCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch(taskPath)
                        .header("Authorization", bearer(otherCaregiver.userId(), scenario.centerId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content(undo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.SESSION_NOT_FOUND.getCode()));

        Scenario otherScenario = newScenario();
        String otherSessionId = createRunningAftercareSession(otherScenario);
        UUID otherTaskId = careTaskRepository.findAllBySession(UUID.fromString(otherSessionId)).getFirst().getId();
        mockMvc.perform(patch("/api/v1/care-sessions/" + sessionId + "/tasks/" + otherTaskId)
                        .header("Authorization", caregiver).contentType(MediaType.APPLICATION_JSON).content(undo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TASK_NOT_FOUND.getCode()));
        mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "UNKNOWN"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(taskPath).header("Authorization", caregiver)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertThat(careTaskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(CareTaskStatus.DONE);
        assertThat(careTaskRepository.findById(otherTaskId).orElseThrow().getStatus()).isEqualTo(CareTaskStatus.PENDING);
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
                .andExpect(jsonPath("$.result.completionRate").value(100.0))
                .andExpect(jsonPath("$.result.occurrencesByStatus.COMPLETED").value(1))
                .andExpect(jsonPath("$.result.trendGroupBy").value("DAY"))
                .andExpect(jsonPath("$.result.trend").isArray())
                .andExpect(jsonPath("$.result.ratingDistribution.5").isNumber());
        mockMvc.perform(get("/api/v1/statistics/centers").header("Authorization", managerBearer(scenario))
                .param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.trendGroupBy").value("MONTH"))
                .andExpect(jsonPath("$.result.trend.length()").value(12));
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
        body.put("bookingType", "RECURRING");
        body.put("frequency", "DAILY");
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
        body.put("bookingType", "RECURRING");
        body.put("frequency", "DAILY");
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

    // ===== Đặc tả booking v1.0 (docs/2026-11-02-BOOKING_FLOW_REVIEW.md) =====

    // Spec §5.2 / §5.3 / §16: RECURRING chưa có caregiver — xác nhận trước, phân công sau, chưa phân thì chưa thanh toán.
    // 2026-11-09 (A + B): khách không chọn caregiver → hệ thống chọn sẵn người rảnh lúc đặt; không ai rảnh thì vẫn đặt và
    // cọc ngay, sau khi cọc scheduler tự phân công, chưa có người thì báo trung tâm (một lần), manager vẫn đổi tay được.
    @Test
    void bookingWithoutCaregiverIsPaidFirstThenAutoAssignedAndManagerCanOverride() throws Exception {
        Scenario picked = newScenario();
        createBooking(picked, weeklyBody(picked, null, 0), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.caregiverId").value(picked.caregiver().caregiverId().toString()))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("RESERVED"));

        Scenario scenario = newScenario();
        PLATFORM.setCaregiverUnavailable(scenario.caregiver().caregiverId());
        MvcResult created = createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.depositDueAt").exists())
                .andExpect(jsonPath("$.result.caregiverId").doesNotExist())
                .andExpect(jsonPath("$.result.initialPaymentPercent").value(30))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("PENDING_ASSIGNMENT"))
                .andReturn();
        String bookingId = readResult(created).get("id").asString();
        UUID id = UUID.fromString(bookingId);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("PENDING_ASSIGNMENT"));

        // Chưa ai rảnh: giữ chưa phân công, báo trung tâm đúng một lần dù thử lại.
        assertThat(bookingExpiryService.findUnassignedDueIds()).doesNotContain(id);
        assertThat(caregiverAssignmentService.findAutoAssignIds()).contains(id);
        caregiverAssignmentService.autoAssign(id);
        caregiverAssignmentService.autoAssign(id);
        assertThat(caregiverAssignmentService.findAutoAssignIds()).doesNotContain(id);
        assertThat(notificationRepository.findAll()).filteredOn(notification -> id.equals(notification.getRefId())
                && notification.getType() == NotificationType.CENTER_ACTION_DUE).hasSize(1);

        CaregiverIds available = PLATFORM.addActiveCaregiver(scenario.centerId());
        caregiverAssignmentService.autoAssign(id);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.caregiverId").value(available.caregiverId().toString()))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.result.occurrences[5].caregiverId").value(available.caregiverId().toString()));
        assertThat(bookingEventRepository.findAllByBooking(id))
                .anyMatch(event -> "Hệ thống tự phân công".equals(event.getReason()));
        assertThat(notificationRepository.findAll()).anyMatch(notification -> id.equals(notification.getRefId())
                && notification.getRecipientUserId().equals(scenario.customerId())
                && notification.getType() == NotificationType.CAREGIVER_ASSIGNED);
        assertCaregiverAccess(available.userId(), scenario.recipientId(), true);

        // Manager ghi đè kết quả tự động.
        CaregiverIds chosenByManager = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", chosenByManager.caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(chosenByManager.caregiverId().toString()));
        assertThat(bookingStatusHistoryRepository.findAllByBooking(id))
                .extracting(BookingStatusHistory::getToStatus)
                .containsExactly(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED);
    }

    // Q-B2: buổi đã cọc còn ≤ 12h mà chưa có người → hệ thống hủy buổi đó và xếp hoàn 100 % (CENTER_CANCEL).
    @Test
    void paidVisitStillUnassignedTwelveHoursBeforeStartIsCancelledWithRefund() throws Exception {
        Scenario scenario = newScenario();
        PLATFORM.setCaregiverUnavailable(scenario.caregiver().caregiverId());
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        UUID id = UUID.fromString(bookingId);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isOk());
        BookingOccurrence first = bookingOccurrenceRepository.findAllByBooking(id).getFirst();
        Instant soon = Instant.now().plus(Duration.ofHours(11));
        jdbcTemplate.update("update booking_occurrences set start_at = ?, end_at = ? where occurrence_id = ?",
                java.sql.Timestamp.from(soon), java.sql.Timestamp.from(soon.plus(Duration.ofHours(2))), first.getId());

        assertThat(bookingExpiryService.findUnassignedDueIds()).contains(id);
        bookingExpiryService.cancelUnassigned(id);
        bookingExpiryService.cancelUnassigned(id);
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.occurrences[0].cancelledByParty").value("SYSTEM"))
                .andExpect(jsonPath("$.result.occurrences[1].status").value("PENDING_ASSIGNMENT"));
        assertThat(bookingOutboxRepository.findAll())
                .filteredOn(outbox -> id.equals(outbox.getAggregateId())
                        && outbox.getIdempotencyKey().startsWith("REFUND:CENTER_CANCEL:"))
                .hasSize(1);
        assertThat(bookingExpiryService.findUnassignedDueIds()).doesNotContain(id);
    }

    // B: caregiver xin hủy buổi → hệ thống tìm người thay ngay (bỏ người vừa xin hủy), khách được báo.
    @Test
    void withdrawnVisitIsAutoReassignedToAnotherFreeCaregiver() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        String firstVisit = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId().toString();
        mockMvc.perform(withdraw(bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"),
                        List.of(firstVisit), "PERSONAL", null))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/booking-occurrences/" + firstVisit).header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.caregiverId").value(replacement.caregiverId().toString()))
                .andExpect(jsonPath("$.result.needsReassignment").value(false))
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"));
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").param("bookingId", bookingId)
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.items[0].state").value("REASSIGNED"));
        assertThat(notificationRepository.findAll()).anyMatch(notification ->
                notification.getRecipientUserId().equals(replacement.userId())
                        && notification.getType() == NotificationType.CAREGIVER_ASSIGNED);
    }

    // ─── Yêu cầu thay đổi v2 (2026-11-03) ───

    // CR-3: khách chọn caregiver → đơn tới thẳng caregiver; trung tâm không duyệt được, caregiver khác không thấy.
    @Test
    void chosenCaregiverAcceptsTheBookingWithoutTheCenter() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        makeLegacyPending(bookingId, BookingStatus.PENDING_CAREGIVER_CONFIRMATION);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        mockMvc.perform(get("/api/v1/bookings/caregiver-requests").header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].id").value(bookingId))
                .andExpect(jsonPath("$.result.items[0].district").value("Hoàn Kiếm"))
                .andExpect(jsonPath("$.result.items[0].occurrences").isNotEmpty())
                .andExpect(jsonPath("$.result.items[0].recipientSnapshot").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].customerId").doesNotExist());
        CaregiverIds stranger = PLATFORM.addActiveCaregiver(scenario.centerId());
        String strangerBearer = bearer(stranger.userId(), scenario.centerId(), "CAREGIVER");
        mockMvc.perform(get("/api/v1/bookings/caregiver-requests").header("Authorization", strangerBearer))
                .andExpect(jsonPath("$.result.items").isEmpty());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-accept").header("Authorization", strangerBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-accept").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-accept").header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"));
        Booking accepted = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(accepted.getAcceptedBy()).isEqualTo(scenario.caregiver().userId());
        assertThat(accepted.getDepositDueAt()).isNotNull();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-accept").header("Authorization", caregiverBearer))
                .andExpect(status().isConflict());
    }

    // CR-3: caregiver từ chối (hoặc quá hạn) đơn RECURRING → trung tâm phân người khác; SHIFT thì bị từ chối hẳn.
    @Test
    void declinedOrUnansweredRecurringBookingIsHandedOverToTheCenter() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        makeLegacyPending(bookingId, BookingStatus.PENDING_CAREGIVER_CONFIRMATION);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-decline")
                        .header("Authorization", caregiverBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-decline")
                        .header("Authorization", caregiverBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Bận việc gia đình"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING_CAREGIVER_ASSIGNMENT"));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.caregiverId").doesNotExist())
                .andExpect(jsonPath("$.result.assignmentDueAt").exists())
                .andExpect(jsonPath("$.result.occurrences[0].status").value("PENDING_ASSIGNMENT"))
                .andExpect(jsonPath("$.result.occurrences[0].caregiverId").doesNotExist());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", scenario.caregiver().caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"));

        // Không trả lời trước hạn → scheduler chuyển trung tâm.
        Scenario second = newScenario();
        String unansweredId = createAndGetId(second, weeklyBody(second, second.caregiver().caregiverId(), 0));
        makeLegacyPending(unansweredId, BookingStatus.PENDING_CAREGIVER_CONFIRMATION);
        Booking unanswered = bookingRepository.findById(UUID.fromString(unansweredId)).orElseThrow();
        unanswered.setAcceptanceDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(unanswered);
        assertThat(bookingExpiryService.findExpirableIds()).contains(unanswered.getId());
        bookingExpiryService.expire(unanswered.getId());
        assertThat(bookingRepository.findById(unanswered.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_CAREGIVER_ASSIGNMENT);
        assertThat(bookingStatusHistoryRepository.findAllByBooking(unanswered.getId()))
                .extracting(BookingStatusHistory::getToStatus)
                .endsWith(BookingStatus.PENDING_CAREGIVER_ASSIGNMENT);
    }

    // CR-2: khách đổi địa chỉ chăm sóc; ghi lịch sử, người khác không đổi được, booking kết thúc thì không đổi nữa.
    @Test
    void customerChangesCareAddressOfUpcomingVisits() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        Map<String, Object> address = Map.of("addressLine", "5 Trần Phú", "ward", "Phường 4", "district", "Quận 5",
                "province", "TP. Hồ Chí Minh", "note", "Bà đang ở nhà con gái");
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressLine", " ", "province", "Hà Nội"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADDRESS_LINE_REQUIRED.getCode()));
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(newScenario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serviceAddress").value("5 Trần Phú, Phường 4, Quận 5, TP. Hồ Chí Minh"))
                .andExpect(jsonPath("$.result.recipientSnapshot.district").value("Quận 5"))
                .andExpect(jsonPath("$.result.recipientSnapshot.fullName").value("Bà Lan"));
        assertThat(bookingEventRepository.findAllByBooking(UUID.fromString(bookingId)))
                .anySatisfy(event -> {
                    assertThat(event.getEventType().name()).isEqualTo("SERVICE_ADDRESS_CHANGED");
                    assertThat(event.getOldValue()).isEqualTo("12 Lý Thường Kiệt, Phường Hàng Bài, Hoàn Kiếm, Hà Nội");
                    assertThat(event.getReason()).isEqualTo("Bà đang ở nhà con gái");
                });
        // Trung tâm của booking cũng đổi được.
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressLine", "7 Trần Phú", "province", "TP. Hồ Chí Minh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serviceAddress").value("7 Trần Phú, TP. Hồ Chí Minh"));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Đổi kế hoạch"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(address)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SERVICE_ADDRESS_NOT_EDITABLE.getCode()));
    }

    // 2026-11-05: khách đổi địa chỉ cho một vài buổi (người bệnh ở nhà con khác vài hôm) — địa chỉ booking giữ nguyên,
    // chỉ buổi được chọn có địa chỉ riêng; buổi không thuộc booking / không còn đổi được → 409.
    @Test
    void customerChangesCareAddressOfChosenVisitsOnly() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        String detail = mockMvc.perform(get("/api/v1/bookings/" + bookingId)
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> occurrenceIds = JsonPath.read(detail, "$.result.occurrences[*].id");
        assertThat(occurrenceIds).hasSizeGreaterThan(1);
        String chosen = occurrenceIds.get(1);

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressLine", "9 Nguyễn Trãi", "province", "Hà Nội",
                                "occurrenceIds", List.of(chosen)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serviceAddress")
                        .value("12 Lý Thường Kiệt, Phường Hàng Bài, Hoàn Kiếm, Hà Nội"))
                .andExpect(jsonPath("$.result.occurrences[?(@.id == '" + chosen + "')].serviceAddress")
                        .value("9 Nguyễn Trãi, Hà Nội"))
                .andExpect(jsonPath("$.result.occurrences[?(@.serviceAddress != null)]", hasSize(1)));

        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/service-address")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("addressLine", "9 Nguyễn Trãi", "province", "Hà Nội",
                                "occurrenceIds", List.of(UUID.randomUUID().toString())))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.ADDRESS_CHANGE_TOO_LATE.getCode()));
    }

    // Spec §5.2 / §15: không phân được caregiver đúng hạn → EXPIRED; trung tâm cũng có thể chủ động từ chối.
    @Test
    void assignmentDeadlineExpiresOrCenterRejects() throws Exception {
        Scenario scenario = newScenario();
        String expiringId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        makeLegacyPending(expiringId, BookingStatus.PENDING_CENTER_CONFIRMATION);
        mockMvc.perform(post("/api/v1/bookings/" + expiringId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.result.status").value("PENDING_CAREGIVER_ASSIGNMENT"));
        Booking booking = bookingRepository.findById(UUID.fromString(expiringId)).orElseThrow();
        booking.setAssignmentDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        mockMvc.perform(patch("/api/v1/bookings/" + expiringId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", scenario.caregiver().caregiverId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(5116));
        // Phân công sau hạn đóng booking ngay (không chờ scheduler).
        assertThat(bookingExpiryService.findExpirableIds()).doesNotContain(booking.getId());
        mockMvc.perform(get("/api/v1/bookings/" + expiringId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.expiredReason").value("ASSIGNMENT_TIMEOUT"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("EXPIRED"));

        Scenario other = newScenarioInCenter(scenario.centerId());
        String rejectedId = createAndGetId(other, weeklyBody(other, null, 0));
        makeLegacyPending(rejectedId, BookingStatus.PENDING_CENTER_CONFIRMATION);
        mockMvc.perform(post("/api/v1/bookings/" + rejectedId + "/accept")
                        .header("Authorization", managerBearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/bookings/" + rejectedId + "/reject")
                        .header("Authorization", managerBearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Không còn người chăm sóc phù hợp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"));
    }

    // Spec §4 / §6 / §15 / §16: SHIFT bắt buộc caregiver + lịch, một lịch chỉ một booking giữ. 2026-11-05: SHIFT đặt cọc
    // theo policy (30 %), phần còn lại trước ca 24h, và tự nhận (ca caregiver đã đăng ký) → chờ khách thanh toán ngay.
    @Test
    void shiftBookingRequiresScheduleAndHoldsItOnce() throws Exception {
        Scenario scenario = newScenario();
        LocalDate workDate = LocalDate.now(ZONE).plusDays(3);
        UUID scheduleId = PLATFORM.addSchedule(scenario.centerId(), scenario.caregiver().caregiverId(), workDate,
                "08:00", "12:00", "SCHEDULED");
        Map<String, Object> body = shiftBody(scenario, scenario.caregiver().caregiverId(), scheduleId);

        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrenceCount").value(1))
                .andExpect(jsonPath("$.result.depositPercent").value(30))
                .andExpect(jsonPath("$.result.estimatedDepositAmount").value(120000));
        createBooking(scenario, body, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingType").value("SHIFT"))
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.depositDueAt").exists())
                .andExpect(jsonPath("$.result.initialPaymentPercent").value(30))
                .andExpect(jsonPath("$.result.remainingPaymentDueAt").exists())
                .andExpect(jsonPath("$.result.startTime").doesNotExist())
                .andExpect(jsonPath("$.result.startDate").value(workDate.toString()))
                .andExpect(jsonPath("$.result.occurrences.length()").value(1))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.result.occurrences[0].caregiverScheduleId").value(scheduleId.toString()));

        // Spec §3: giao diện biết lịch đã bị giữ để khóa lựa chọn (không lộ booking / khách).
        Scenario second = newScenarioInCenter(scenario.centerId());
        mockMvc.perform(get("/api/v1/bookings/held-schedules")
                        .param("caregiverId", scenario.caregiver().caregiverId().toString())
                        .header("Authorization", customerBearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.heldScheduleIds.length()").value(1))
                .andExpect(jsonPath("$.result.heldScheduleIds[0]").value(scheduleId.toString()))
                .andExpect(jsonPath("$.result.busySlots.length()").value(1))
                .andExpect(jsonPath("$.result.busySlots[0].bookingId").doesNotExist());
        mockMvc.perform(get("/api/v1/bookings/held-schedules")
                        .param("caregiverId", scenario.caregiver().caregiverId().toString())
                        .param("from", workDate.toString())
                        .param("to", workDate.plusDays(40).toString())
                        .header("Authorization", customerBearer(second)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_DATE_FILTER.getCode()));
        // Khách khác đặt cùng lịch → 409, không tạo booking.
        createBooking(second, shiftBody(second, scenario.caregiver().caregiverId(), scheduleId), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_NOT_AVAILABLE.getCode()));

        Map<String, Object> withoutSchedule = shiftBody(second, scenario.caregiver().caregiverId(), scheduleId);
        withoutSchedule.remove("caregiverScheduleId");
        createBooking(second, withoutSchedule, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_ID_REQUIRED.getCode()));
        Map<String, Object> withoutCaregiver = shiftBody(second, scenario.caregiver().caregiverId(), scheduleId);
        withoutCaregiver.remove("caregiverId");
        createBooking(second, withoutCaregiver, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_ID_REQUIRED.getCode()));
        CaregiverIds otherCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        createBooking(second, shiftBody(second, otherCaregiver.caregiverId(), scheduleId), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_MISMATCH.getCode()));
        UUID cancelledSchedule = PLATFORM.addSchedule(scenario.centerId(), otherCaregiver.caregiverId(),
                workDate.plusDays(1), "08:00", "12:00", "CANCELLED");
        createBooking(second, shiftBody(second, otherCaregiver.caregiverId(), cancelledSchedule), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_NOT_AVAILABLE.getCode()));
    }

    // ===== 2026-11-09: giữ chỗ 5 phút, caregiver xin hủy buổi, khách đổi caregiver cả booking =====

    @Test
    void shiftHoldLastsFiveMinutesAcceptsLatePaymentWithinGraceThenReleasesTheSchedule() throws Exception {
        Scenario scenario = newScenario();
        LocalDate workDate = LocalDate.now(ZONE).plusDays(3);
        UUID scheduleId = PLATFORM.addSchedule(scenario.centerId(), scenario.caregiver().caregiverId(), workDate,
                "08:00", "12:00", "SCHEDULED");
        Instant before = Instant.now();
        String bookingId = readResult(createBooking(scenario, shiftBody(scenario, scenario.caregiver().caregiverId(),
                        scheduleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.deadlineType").value("INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.serverTime").exists())
                .andReturn()).get("id").asString();
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(booking.getDepositDueAt()).isBetween(before.plus(Duration.ofMinutes(5)),
                Instant.now().plus(Duration.ofMinutes(5)));

        // Quá hạn khách thấy nhưng còn trong grace 2 phút: chưa nhả slot, tiền về muộn vẫn được xác nhận.
        booking.setDepositDueAt(Instant.now().minusSeconds(60));
        bookingRepository.save(booking);
        assertThat(bookingExpiryService.findExpiredHoldIds()).doesNotContain(booking.getId());
        bookingExpiryService.expire(booking.getId());
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentReference", "PAY-LATE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        // Qua cả grace: job nhả slot, cọc tới sau đó bị từ chối, khách khác đặt được đúng ca đó.
        Scenario late = newScenarioInCenter(scenario.centerId());
        UUID lateSchedule = PLATFORM.addSchedule(late.centerId(), late.caregiver().caregiverId(), workDate,
                "08:00", "12:00", "SCHEDULED");
        String lateBookingId = createAndGetId(late, shiftBody(late, late.caregiver().caregiverId(), lateSchedule));
        Booking lateBooking = bookingRepository.findById(UUID.fromString(lateBookingId)).orElseThrow();
        lateBooking.setDepositDueAt(Instant.now().minus(Duration.ofMinutes(3)));
        bookingRepository.save(lateBooking);
        assertThat(bookingExpiryService.findExpiredHoldIds()).contains(lateBooking.getId());
        mockMvc.perform(post("/api/v1/bookings/" + lateBookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isConflict());
        bookingExpiryService.expire(lateBooking.getId());
        mockMvc.perform(get("/api/v1/bookings/" + lateBookingId).header("Authorization", customerBearer(late)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.expiredReason").value("INITIAL_PAYMENT_TIMEOUT"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("EXPIRED"));
        Scenario next = newScenarioInCenter(scenario.centerId());
        createBooking(next, shiftBody(next, late.caregiver().caregiverId(), lateSchedule), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"));
    }

    @Test
    void caregiverWithdrawsVisitsMoreThanADayAheadAndManagerReassigns() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        JsonNode visits = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get("items");
        String firstVisit = visits.get(0).get("id").asString();
        String secondVisit = visits.get(1).get("id").asString();
        String thirdVisit = visits.get(2).get("id").asString();

        // Lọc buổi của caregiver theo booking (trang chi tiết booking của caregiver).
        mockMvc.perform(get("/api/v1/booking-occurrences").param("bookingId", bookingId)
                        .header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(visits.size()))
                .andExpect(jsonPath("$.result.items[0].needsReassignment").value(false));
        mockMvc.perform(get("/api/v1/booking-occurrences").param("bookingId", UUID.randomUUID().toString())
                        .header("Authorization", caregiverBearer))
                .andExpect(jsonPath("$.result.totalElements").value(0));

        mockMvc.perform(withdraw(caregiverBearer, List.of(firstVisit), "OTHER", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.WITHDRAWAL_REASON_DETAIL_REQUIRED.getCode()));
        CaregiverIds stranger = PLATFORM.addActiveCaregiver(scenario.centerId());
        // Không ai rảnh để tự phân công → buổi chờ trung tâm phân tay (luồng tự phân công có test riêng).
        PLATFORM.setCaregiverUnavailable(stranger.caregiverId());
        mockMvc.perform(withdraw(bearer(stranger.userId(), scenario.centerId(), "CAREGIVER"), List.of(firstVisit),
                        "PERSONAL", null))
                .andExpect(status().isNotFound());
        // Buổi còn ≤ 24h → không hủy được; một buổi sai thì cả lệnh bị từ chối.
        Instant soon = Instant.now().plus(Duration.ofHours(23));
        jdbcTemplate.update("update booking_occurrences set start_at = ?, end_at = ? where occurrence_id = ?",
                java.sql.Timestamp.from(soon), java.sql.Timestamp.from(soon.plus(Duration.ofHours(2))),
                UUID.fromString(thirdVisit));
        mockMvc.perform(withdraw(caregiverBearer, List.of(firstVisit, thirdVisit), "PERSONAL", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.WITHDRAWAL_TOO_LATE.getCode()));

        mockMvc.perform(withdraw(caregiverBearer, List.of(firstVisit, secondVisit), "OTHER", "Con ốm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].state").value("PENDING"))
                .andExpect(jsonPath("$.result[0].reasonDetail").value("Con ốm"));
        mockMvc.perform(withdraw(caregiverBearer, List.of(firstVisit), "PERSONAL", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.WITHDRAWAL_ALREADY_REQUESTED.getCode()));
        mockMvc.perform(get("/api/v1/booking-occurrences/" + firstVisit).header("Authorization", caregiverBearer))
                .andExpect(jsonPath("$.result.needsReassignment").value(true));
        assertThat(bookingEventRepository.findAllByBooking(UUID.fromString(bookingId)))
                .extracting(event -> event.getEventType().name())
                .contains("CAREGIVER_WITHDRAWN");
        assertThat(notificationRepository.findAll())
                .anyMatch(notification -> notification.getRecipientUserId().equals(scenario.customerId())
                        && notification.getType() == NotificationType.CAREGIVER_UNASSIGNED);

        // Hàng đợi của trung tâm (chỉ trung tâm mình) và lịch sử của caregiver.
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").param("pendingOnly", "true")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.items[0].bookingCode").exists());
        mockMvc.perform(get("/api/v1/caregiver-withdrawals")
                        .header("Authorization", bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER")))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").param("bookingId", bookingId)
                        .header("Authorization", caregiverBearer))
                .andExpect(jsonPath("$.result.totalElements").value(2));
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());

        // Manager phân người khác cho một buổi → cờ tự bỏ, yêu cầu thành REASSIGNED, hàng đợi còn một buổi.
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/api/v1/bookings/" + bookingId + "/occurrences/" + firstVisit + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.needsReassignment").value(false));
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").param("pendingOnly", "true")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].occurrenceId").value(secondVisit));
        mockMvc.perform(get("/api/v1/caregiver-withdrawals").param("bookingId", bookingId)
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(jsonPath("$.result.items[?(@.occurrenceId == '" + firstVisit + "')].state").value("REASSIGNED"));
    }

    // 2026-11-10: ngày có nhiều buổi thì caregiver phải xin hủy cả ngày; nhận nhanh nhiều buổi trong một lệnh.
    @Test
    void caregiverAcknowledgesManyVisitsAndWithdrawsWholeDays() throws Exception {
        Scenario scenario = newScenario();
        LocalDate busyDay = LocalDate.now(ZONE).plusDays(5);
        LocalDate quietDay = busyDay.plusDays(2);
        Map<String, Object> body = customBody(scenario, List.of(
                shiftSlot(busyDay, "08:00", "10:00", null),
                shiftSlot(busyDay, "13:00", "15:00", null),
                shiftSlot(quietDay, "08:00", "10:00", null)));
        body.put("caregiverId", scenario.caregiver().caregiverId().toString());
        String bookingId = createAndGetId(scenario, body);
        acceptAndConfirm(scenario, bookingId, null);
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        List<BookingOccurrence> visits = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        String morning = visits.get(0).getId().toString();
        String afternoon = visits.get(1).getId().toString();
        String later = visits.get(2).getId().toString();

        // Nhận nhanh: buổi của người khác → 404; cả lệnh thành công thì mọi buổi ACKNOWLEDGED, gửi lại → 409.
        CaregiverIds stranger = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(acknowledgeMany(bearer(stranger.userId(), scenario.centerId(), "CAREGIVER"),
                        List.of(morning)))
                .andExpect(status().isNotFound());
        mockMvc.perform(acknowledgeMany(caregiverBearer, List.of()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(acknowledgeMany(caregiverBearer, List.of(morning, afternoon)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.result[1].status").value("ACKNOWLEDGED"));
        mockMvc.perform(acknowledgeMany(caregiverBearer, List.of(afternoon, later)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_OCCURRENCE_TRANSITION.getCode()));
        // Lệnh bị từ chối thì không buổi nào đổi trạng thái.
        assertThat(bookingOccurrenceRepository.findById(UUID.fromString(later)).orElseThrow().getStatus())
                .isEqualTo(OccurrenceStatus.SCHEDULED);

        // Hủy lẻ một buổi của ngày có hai buổi → phải hủy cả ngày; ngày chỉ một buổi thì hủy lẻ được.
        mockMvc.perform(withdraw(caregiverBearer, List.of(morning), "PERSONAL", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.WITHDRAWAL_WHOLE_DAY_REQUIRED.getCode()));
        mockMvc.perform(withdraw(caregiverBearer, List.of(morning, afternoon), "PERSONAL", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2));
        mockMvc.perform(withdraw(caregiverBearer, List.of(later), "PERSONAL", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
    }

    org.springframework.test.web.servlet.RequestBuilder acknowledgeMany(String caregiverBearer,
                                                                        List<String> occurrenceIds) {
        return post("/api/v1/booking-occurrences/acknowledgements")
                .header("Authorization", caregiverBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("occurrenceIds", occurrenceIds)));
    }

    @Test
    void customerCaregiverChangeAppliesToEveryRemainingVisit() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        JsonNode visits = readResult(mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get("items");

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-change-requests")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scopeType", "SPECIFIC_OCCURRENCES", "reasonCode", "PERSONAL_PREFERENCE",
                                "occurrenceIds", List.of(visits.get(0).get("id").asString())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_CHANGE_SCOPE_INVALID.getCode()));
        String requestId = readResult(mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-change-requests")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scopeType", "ALL_REMAINING", "reasonCode", "PERSONAL_PREFERENCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrenceIds.length()").value(visits.size()))
                .andReturn()).get("id").asString();

        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(post("/api/v1/caregiver-change-requests/" + requestId + "/approve")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newCaregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPROVED"));
        mockMvc.perform(post("/api/v1/caregiver-change-requests/" + requestId + "/approve")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newCaregiverId", replacement.caregiverId().toString()))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.caregiverId").value(replacement.caregiverId().toString()))
                .andExpect(jsonPath("$.result.occurrences[?(@.caregiverId == '" + scenario.caregiver().caregiverId()
                        + "')]").isEmpty());
        assertCaregiverAccess(replacement.userId(), scenario.recipientId(), true);
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
    }

    org.springframework.test.web.servlet.RequestBuilder withdraw(String caregiverBearer, List<String> occurrenceIds,
                                                                 String reasonCode, String reasonDetail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("occurrenceIds", occurrenceIds);
        body.put("reasonCode", reasonCode);
        if (reasonDetail != null) {
            body.put("reasonDetail", reasonDetail);
        }
        return post("/api/v1/caregiver-withdrawals")
                .header("Authorization", caregiverBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body));
    }

    // 2026-11-05: ca 08:00–20:00 chia khung giờ theo gói, mỗi khung giờ một dịch vụ; booking khác cách 60 phút; tối đa
    // maxBookings booking trên ca.
    @Test
    void shiftSplitsIntoServiceSlotsWithTravelBufferAndMaxBookings() throws Exception {
        UUID centerId = PLATFORM.addCenter("ACTIVE");
        UUID secondServiceId = UUID.randomUUID();
        CaregiverIds caregiver = PLATFORM.addCaregiver(centerId, "VERIFIED", "ACTIVE",
                List.of(TestPlatformServer.DEFAULT_CARE_SERVICE_ID, secondServiceId));
        Scenario scenario = newScenarioInCenter(centerId);
        LocalDate workDate = LocalDate.now(ZONE).plusDays(3);
        UUID scheduleId = PLATFORM.addSchedule(centerId, caregiver.caregiverId(), workDate, "08:00", "20:00",
                "SCHEDULED", 2);
        UUID bathingPlan = UUID.randomUUID();
        UUID feedingPlan = UUID.randomUUID();
        PLATFORM.addPricePlan(bathingPlan, "SHIFT", 240, 400000, "Ca 4 giờ");
        PLATFORM.addPricePlan(feedingPlan, "SHIFT", 240, 300000, "Ca 4 giờ tiết kiệm");
        Map<String, Object> body = shiftBody(scenario, caregiver.caregiverId(), scheduleId);
        body.put("services", List.of(
                Map.of("careServiceId", scenario.careServiceId().toString(), "pricePlanId", bathingPlan.toString()),
                Map.of("careServiceId", secondServiceId.toString(), "pricePlanId", feedingPlan.toString())));
        body.put("slots", List.of(
                shiftSlot(workDate, "08:00", "12:00", scenario.careServiceId()),
                shiftSlot(workDate, "12:00", "16:00", secondServiceId)));

        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrenceCount").value(2))
                .andExpect(jsonPath("$.result.totalAmount").value(700000))
                .andExpect(jsonPath("$.result.lines[0].planName").value("Ca 4 giờ"))
                .andExpect(jsonPath("$.result.lines[0].planDurationMinutes").value(240))
                .andExpect(jsonPath("$.result.lines[0].occurrenceCount").value(1))
                .andExpect(jsonPath("$.result.occurrences[0].careServiceId").value(scenario.careServiceId().toString()))
                .andExpect(jsonPath("$.result.occurrences[1].careServiceId").value(secondServiceId.toString()));
        createBooking(scenario, body, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrences.length()").value(2))
                .andExpect(jsonPath("$.result.occurrences[0].careServiceId").value(scenario.careServiceId().toString()))
                .andExpect(jsonPath("$.result.occurrences[1].caregiverScheduleId").value(scheduleId.toString()))
                .andExpect(jsonPath("$.result.services[*].planName").value(
                        Matchers.containsInAnyOrder("Ca 4 giờ", "Ca 4 giờ tiết kiệm")))
                .andExpect(jsonPath("$.result.totalAmount").value(700000));

        Scenario second = newScenarioInCenter(centerId);
        mockMvc.perform(get("/api/v1/bookings/held-schedules")
                        .param("caregiverId", caregiver.caregiverId().toString())
                        .header("Authorization", customerBearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.travelBufferMinutes").value(60))
                .andExpect(jsonPath("$.result.scheduleBookingCounts['" + scheduleId + "']").value(1))
                .andExpect(jsonPath("$.result.busySlots.length()").value(2));

        // Booking khác bắt đầu đúng 16:00 → thiếu 60 phút trừ hao; 17:00–20:00 (gói theo giờ) thì được.
        Map<String, Object> adjacent = shiftBody(second, caregiver.caregiverId(), scheduleId);
        adjacent.put("slots", List.of(shiftSlot(workDate, "16:00", "19:00", null)));
        createBooking(second, adjacent, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_TRAVEL_BUFFER_CONFLICT.getCode()));
        adjacent.put("slots", List.of(shiftSlot(workDate, "17:00", "20:00", null)));
        createBooking(second, adjacent, null).andExpect(status().isOk());

        // Ca đã đủ 2 booking → khóa.
        Scenario third = newScenarioInCenter(centerId);
        Map<String, Object> full = shiftBody(third, caregiver.caregiverId(), scheduleId);
        full.put("slots", List.of(shiftSlot(workDate, "08:00", "09:00", null)));
        createBooking(third, full, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_NOT_AVAILABLE.getCode()));

        // Quy tắc khung giờ: ngoài ca, gói cố định sai độ dài, trộn có / không dịch vụ.
        UUID otherSchedule = PLATFORM.addSchedule(centerId, caregiver.caregiverId(), workDate.plusDays(1), "08:00",
                "20:00", "SCHEDULED", 2);
        Map<String, Object> outside = shiftBody(third, caregiver.caregiverId(), otherSchedule);
        outside.put("slots", List.of(shiftSlot(workDate.plusDays(1), "18:00", "21:00", null)));
        createBooking(third, outside, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLOT_OUTSIDE_SHIFT.getCode()));
        Map<String, Object> wrongLength = shiftBody(third, caregiver.caregiverId(), otherSchedule);
        wrongLength.put("services", List.of(
                Map.of("careServiceId", third.careServiceId().toString(), "pricePlanId", bathingPlan.toString())));
        wrongLength.put("slots", List.of(shiftSlot(workDate.plusDays(1), "08:00", "11:00", third.careServiceId())));
        createBooking(third, wrongLength, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRICE_PLAN_UNIT_MISMATCH.getCode()));
        Map<String, Object> mixed = shiftBody(third, caregiver.caregiverId(), otherSchedule);
        mixed.put("slots", List.of(shiftSlot(workDate.plusDays(1), "08:00", "10:00", third.careServiceId()),
                shiftSlot(workDate.plusDays(1), "10:00", "12:00", null)));
        createBooking(third, mixed, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLOT_SERVICE_MIXED.getCode()));
    }

    // Hai dịch vụ theo giờ trong ca 08:00–20:00: 08–09 dịch vụ 1, 09–11 + 11–13 dịch vụ 2 (báo lỗi 2026-11-05).
    @Test
    void shiftQuotesTwoHourlyServicesWithBackToBackSlots() throws Exception {
        UUID centerId = PLATFORM.addCenter("ACTIVE");
        UUID secondServiceId = UUID.randomUUID();
        CaregiverIds caregiver = PLATFORM.addCaregiver(centerId, "VERIFIED", "ACTIVE",
                List.of(TestPlatformServer.DEFAULT_CARE_SERVICE_ID, secondServiceId));
        Scenario scenario = newScenarioInCenter(centerId);
        LocalDate workDate = LocalDate.now(ZONE).plusDays(3);
        UUID scheduleId = PLATFORM.addSchedule(centerId, caregiver.caregiverId(), workDate, "08:00", "20:00",
                "SCHEDULED", 2);
        UUID elderlyPlan = UUID.randomUUID();
        UUID therapyPlan = UUID.randomUUID();
        PLATFORM.addPricePlan(elderlyPlan, "HOUR", 60, 100000, "Theo giờ");
        PLATFORM.addPricePlan(therapyPlan, "HOUR", 60, 150000, "Buổi tập 60 phút");
        Map<String, Object> body = shiftBody(scenario, caregiver.caregiverId(), scheduleId);
        body.put("services", List.of(
                Map.of("careServiceId", scenario.careServiceId().toString(), "pricePlanId", elderlyPlan.toString()),
                Map.of("careServiceId", secondServiceId.toString(), "pricePlanId", therapyPlan.toString())));
        body.put("slots", List.of(
                shiftSlot(workDate, "08:00", "09:00", scenario.careServiceId()),
                shiftSlot(workDate, "09:00", "11:00", secondServiceId),
                shiftSlot(workDate, "11:00", "13:00", secondServiceId)));

        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andDo(result -> System.out.println("QUOTE-REPRO " + result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.occurrenceCount").value(3))
                .andExpect(jsonPath("$.result.totalAmount").value(700000));
    }

    Map<String, Object> shiftSlot(LocalDate day, String start, String end, UUID careServiceId) {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("startAt", day.atTime(LocalTime.parse(start)).atZone(ZONE).toInstant().toString());
        slot.put("endAt", day.atTime(LocalTime.parse(end)).atZone(ZONE).toInstant().toString());
        if (careServiceId != null) {
            slot.put("careServiceId", careServiceId.toString());
        }
        return slot;
    }

    // Spec §4 / §6 / §8: CUSTOM mỗi buổi một khung giờ, không chồng nhau, đặt trước tối thiểu theo policy (48h).
    @Test
    void customBookingUsesOwnSlotsAndPolicyMinAdvance() throws Exception {
        Scenario scenario = newScenario();
        LocalDate day = LocalDate.now(ZONE).plusDays(5);
        Instant firstStart = day.atTime(8, 0).atZone(ZONE).toInstant();
        Instant secondStart = day.plusDays(2).atTime(14, 0).atZone(ZONE).toInstant();
        Map<String, Object> body = customBody(scenario, List.of(
                Map.of("startAt", secondStart.toString(), "endAt", secondStart.plusSeconds(3 * 3600).toString()),
                Map.of("startAt", firstStart.toString(), "endAt", firstStart.plusSeconds(3600).toString())));
        createBooking(scenario, body, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingType").value("CUSTOM"))
                .andExpect(jsonPath("$.result.recurrenceFrequency").doesNotExist())
                .andExpect(jsonPath("$.result.startTime").doesNotExist())
                .andExpect(jsonPath("$.result.occurrences.length()").value(2))
                .andExpect(jsonPath("$.result.occurrences[0].durationMinutes").value(60))
                .andExpect(jsonPath("$.result.occurrences[1].durationMinutes").value(180))
                // Không chọn caregiver → hệ thống giữ sẵn người rảnh của trung tâm (2026-11-09).
                .andExpect(jsonPath("$.result.occurrences[0].status").value("RESERVED"));

        Scenario second = newScenarioInCenter(scenario.centerId());
        Instant overlapStart = day.plusDays(10).atTime(8, 0).atZone(ZONE).toInstant();
        createBooking(second, customBody(second, List.of(
                Map.of("startAt", overlapStart.toString(), "endAt", overlapStart.plusSeconds(7200).toString()),
                Map.of("startAt", overlapStart.plusSeconds(3600).toString(),
                        "endAt", overlapStart.plusSeconds(10800).toString()))), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.OCCURRENCES_OVERLAP.getCode()));
        // Mặc định CUSTOM phải đặt trước 48 giờ.
        Instant tooSoon = Instant.now().plusSeconds(24 * 3600);
        createBooking(second, customBody(second, List.of(
                Map.of("startAt", tooSoon.toString(), "endAt", tooSoon.plusSeconds(3600).toString()))), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.LEAD_TIME_TOO_SHORT.getCode()));
        Map<String, Object> mixed = customBody(second, List.of(
                Map.of("startAt", overlapStart.toString(), "endAt", overlapStart.plusSeconds(3600).toString())));
        mixed.put("frequency", "DAILY");
        createBooking(second, mixed, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.SCHEDULE_FIELD_NOT_ALLOWED.getCode()));
    }

    // Spec §8 / §12: manager sửa policy trung tâm mình, admin mọi trung tâm, khách chỉ xem; booking đọc policy lúc xử lý.
    @Test
    void centerBookingPolicyScopesAndDrivesInitialPayment() throws Exception {
        Scenario scenario = newScenario();
        String policyUrl = "/api/v1/center-booking-policies/" + scenario.centerId();
        mockMvc.perform(get(policyUrl).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.customized").value(false))
                .andExpect(jsonPath("$.result.depositPercent").value(30))
                .andExpect(jsonPath("$.result.shiftMinAdvanceMinutes").value(120))
                .andExpect(jsonPath("$.result.recurringMinAdvanceMinutes").value(2880))
                .andExpect(jsonPath("$.result.refundPercentAfterThreshold").value(70))
                .andExpect(jsonPath("$.result.lateCancelNoRefundMinutes").value(120))
                .andExpect(jsonPath("$.result.overduePaymentForfeitPercent").value(100))
                .andExpect(jsonPath("$.result.remainingPaymentReminderMinutes[0]").value(4320));

        Map<String, Object> policy = policyBody(40, null);
        mockMvc.perform(put(policyUrl).header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policy)))
                .andExpect(status().isForbidden());
        Scenario otherCenter = newScenario();
        mockMvc.perform(put("/api/v1/center-booking-policies/" + otherCenter.centerId())
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policy)))
                .andExpect(status().isNotFound());
        Map<String, Object> inconsistent = policyBody(40, null);
        inconsistent.put("recurringMinAdvanceMinutes", 600);
        mockMvc.perform(put(policyUrl).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(inconsistent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_POLICY.getCode()));
        // Mốc "không hoàn" dài hơn mốc hoàn → 400; thiếu → 400.
        Map<String, Object> lateTooLong = policyBody(40, null);
        lateTooLong.put("refundThresholdMinutes", 60);
        lateTooLong.put("lateCancelNoRefundMinutes", 90);
        mockMvc.perform(put(policyUrl).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(lateTooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_POLICY.getCode()));
        Map<String, Object> lateMissing = policyBody(40, null);
        lateMissing.remove("lateCancelNoRefundMinutes");
        mockMvc.perform(put(policyUrl).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(lateMissing)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_POLICY_VALUE_REQUIRED.getCode()));
        mockMvc.perform(put(policyUrl).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policy)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.customized").value(true))
                .andExpect(jsonPath("$.result.depositPercent").value(40))
                .andExpect(jsonPath("$.result.version").value(0));
        // Sửa trên bản cũ (thiếu version) → 409.
        mockMvc.perform(put(policyUrl).header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON).content(json(policyBody(50, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_UPDATE.getCode()));
        mockMvc.perform(put("/api/v1/center-booking-policies/" + otherCenter.centerId())
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON).content(json(policyBody(25, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.depositPercent").value(25));

        mockMvc.perform(post("/api/v1/bookings/quotes")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.depositPercent").value(40));
        createBooking(scenario, weeklyBody(scenario, null, 0), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.initialPaymentPercent").value(40));
    }

    // 2026-11-05: ca bắt đầu trong vòng mốc trả phần còn lại (24h) → không còn hạn phần còn lại, khách trả 100 % ngay.
    @Test
    void shiftBookedInsideTheRemainingCutoffIsPaidInFull() throws Exception {
        Scenario scenario = newScenario();
        ZonedDateTime start = ZonedDateTime.now(ZONE).plusHours(4).truncatedTo(ChronoUnit.HOURS);
        Assumptions.assumeTrue(start.getHour() <= 22, "ca 1 giờ phải nằm trong cùng ngày");
        UUID scheduleId = PLATFORM.addSchedule(scenario.centerId(), scenario.caregiver().caregiverId(),
                start.toLocalDate(), String.format("%02d:00", start.getHour()),
                String.format("%02d:00", start.getHour() + 1), "SCHEDULED");

        createBooking(scenario, shiftBody(scenario, scenario.caregiver().caregiverId(), scheduleId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"))
                .andExpect(jsonPath("$.result.initialPaymentPercent").value(100))
                .andExpect(jsonPath("$.result.remainingPaymentDueAt").doesNotExist());
        // SHIFT tự nhận nên không nằm trong danh sách chờ caregiver trả lời.
        mockMvc.perform(get("/api/v1/bookings/caregiver-requests")
                        .header("Authorization", bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(0));
    }

    Map<String, Object> shiftBody(Scenario scenario, UUID caregiverId, UUID scheduleId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientId", scenario.recipientId().toString());
        body.put("centerId", scenario.centerId().toString());
        body.put("bookingType", "SHIFT");
        body.put("caregiverId", caregiverId.toString());
        body.put("caregiverScheduleId", scheduleId.toString());
        body.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString(),
                "pricePlanId", scenario.pricePlanId().toString())));
        return body;
    }

    Map<String, Object> customBody(Scenario scenario, List<Map<String, Object>> slots) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipientId", scenario.recipientId().toString());
        body.put("centerId", scenario.centerId().toString());
        body.put("bookingType", "CUSTOM");
        body.put("slots", slots);
        body.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString(),
                "pricePlanId", scenario.pricePlanId().toString())));
        return body;
    }

    Map<String, Object> policyBody(int depositPercent, Long version) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shiftMinAdvanceMinutes", 120);
        body.put("recurringMinAdvanceMinutes", 2880);
        body.put("customMinAdvanceMinutes", 2880);
        body.put("confirmationTimeoutMinutes", 1440);
        body.put("assignmentTimeoutMinutes", 1440);
        body.put("initialPaymentTimeoutMinutes", 1440);
        body.put("remainingPaymentDueBeforeMinutes", 1440);
        body.put("remainingPaymentReminderMinutes", List.of(4320, 2880));
        body.put("depositPercent", depositPercent);
        body.put("checkInEarlyMinutes", 30);
        body.put("checkInLateMinutes", 30);
        body.put("refundThresholdMinutes", 1440);
        body.put("refundPercentBeforeThreshold", 100);
        body.put("refundPercentAfterThreshold", 70);
        body.put("lateCancelNoRefundMinutes", 120);
        body.put("overduePaymentForfeitPercent", 100);
        if (version != null) {
            body.put("version", version);
        }
        return body;
    }

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
        body.put("bookingType", "RECURRING");
        body.put("frequency", "WEEKLY");
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

    // Xác nhận + trả đủ (giả lập PaymentService báo khoản đầu đã phủ toàn bộ) để các test chăm sóc check-in được.
    void acceptAndConfirm(Scenario scenario, String bookingId, UUID caregiverId) throws Exception {
        acceptAndPayInitial(scenario, bookingId, caregiverId, true);
    }

    void acceptAndPayInitial(Scenario scenario, String bookingId, UUID caregiverId, boolean fullyPaid) throws Exception {
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        if (booking.getStatus() == BookingStatus.AWAITING_INITIAL_PAYMENT) {
            // 2026-11-09 (A): booking mới chờ cọc ngay khi đặt — không còn bước trung tâm / caregiver xác nhận.
        } else if (booking.getStatus() == BookingStatus.PENDING_CAREGIVER_CONFIRMATION) {
            // CR-3: khách đã chọn caregiver → chính caregiver nhận đơn, không qua trung tâm.
            mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/caregiver-accept")
                            .header("Authorization", bearer(booking.getCaregiverUserId(), scenario.centerId(), "CAREGIVER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("AWAITING_INITIAL_PAYMENT"));
        } else {
            String acceptBody = caregiverId == null ? "{}" : json(Map.of("caregiverId", caregiverId.toString()));
            mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                            .header("Authorization", managerBearer(scenario))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(acceptBody))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/deposit-paid")
                        .header("Authorization", serviceBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentReference", "PAY-" + bookingId, "fullyPaid", fullyPaid))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
    }

    // ─── Đợt 4: trả đủ trước buổi đầu (spec §9.1, §14), quá hạn (M1), nhắc (M6) ───

    @Test
    void checkInNeedsFullPaymentAndRemainingPaidIsIdempotent() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        // Chưa xác nhận (booking cũ còn chờ caregiver nhận) → chưa nhận phần còn lại.
        makeLegacyPending(bookingId, BookingStatus.PENDING_CAREGIVER_CONFIRMATION);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/remaining-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        acceptAndPayInitial(scenario, bookingId, null, false);
        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getFullyPaidAt()).isNull();

        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveStartAgo(occurrenceId, 10);
        startSession(caregiverBearer, occurrenceId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_NOT_FULLY_PAID.getCode()));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/remaining-paid")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/remaining-paid")
                            .header("Authorization", serviceBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("paymentReference", "PAY-REMAINING"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.result.fullyPaidAt").exists());
        }
        startSession(caregiverBearer, occurrenceId).andExpect(status().isOk());
    }

    @Test
    void paymentOverdueExpiresConfirmedBookingAndFreesTheCaregiver() throws Exception {
        Scenario scenario = newScenario();
        UUID caregiverId = scenario.caregiver().caregiverId();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, caregiverId, 0));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/payment-overdue").header("Authorization", serviceBearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        acceptAndPayInitial(scenario, bookingId, null, false);
        // payment-context mang center policy để PaymentService chụp lúc thanh toán (hoàn 100 / 70, giữ cọc khi quá hạn).
        mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/payment-context").header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.refundThresholdMinutes").value(1440))
                .andExpect(jsonPath("$.result.refundPercentBeforeThreshold").value(100))
                .andExpect(jsonPath("$.result.refundPercentAfterThreshold").value(70))
                .andExpect(jsonPath("$.result.lateCancelNoRefundMinutes").value(120))
                .andExpect(jsonPath("$.result.overduePaymentForfeitPercent").value(100));

        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/payment-overdue")
                        .header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/payment-overdue")
                            .header("Authorization", serviceBearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("EXPIRED"));
        }
        UUID id = UUID.fromString(bookingId);
        assertThat(bookingOccurrenceRepository.findAllByBooking(id))
                .extracting(BookingOccurrence::getStatus)
                .containsOnly(OccurrenceStatus.EXPIRED);
        BookingStatusHistory last = bookingStatusHistoryRepository.findAllByBooking(id).getLast();
        assertThat(last.getToStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(last.getReason()).isEqualTo("PAYMENT_OVERDUE");
        // Payment không gửi refund qua Booking: quá hạn đã giữ / hoàn cọc ở PaymentService.
        assertThat(bookingOutboxRepository.findAll()).noneMatch(outbox -> outbox.getAggregateId().equals(id));
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/remaining-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isConflict());
        // Lịch caregiver được giải phóng: đặt lại đúng khung giờ đó thành công.
        createBooking(scenario, weeklyBody(scenario, caregiverId, 0), null).andExpect(status().isOk());
    }

    @Test
    void remainingPaymentReminderFiresOncePerPolicyMark() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndPayInitial(scenario, bookingId, null, false);
        UUID id = UUID.fromString(bookingId);
        long before = notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(scenario.customerId());

        setRemainingDue(id, Duration.ofHours(100));
        bookingExpiryService.remindRemainingPayment(id);
        assertThat(bookingRepository.findById(id).orElseThrow().getRemainingReminderMinutes()).isNull();

        // Tới mốc 72h → nhắc một lần; quét lại không nhắc trùng.
        setRemainingDue(id, Duration.ofHours(71));
        assertThat(bookingExpiryService.findRemainingReminderIds()).contains(id);
        bookingExpiryService.remindRemainingPayment(id);
        bookingExpiryService.remindRemainingPayment(id);
        assertThat(bookingRepository.findById(id).orElseThrow().getRemainingReminderMinutes()).isEqualTo(4320);
        assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(scenario.customerId()))
                .isEqualTo(before + 1);

        // Tới mốc 48h → nhắc lần hai.
        bookingRepository.findById(id).ifPresent(booking -> {
            booking.setRemainingPaymentDueAt(Instant.now().plus(Duration.ofHours(47)));
            bookingRepository.save(booking);
        });
        bookingExpiryService.remindRemainingPayment(id);
        assertThat(bookingRepository.findById(id).orElseThrow().getRemainingReminderMinutes()).isEqualTo(2880);
        assertThat(notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(scenario.customerId()))
                .isEqualTo(before + 2);

        // Trả đủ → không còn trong danh sách nhắc.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/remaining-paid").header("Authorization", serviceBearer()))
                .andExpect(status().isOk());
        assertThat(bookingExpiryService.findRemainingReminderIds()).doesNotContain(id);
    }

    // Giả lập booking tạo trước spec v1 (change script để remaining_payment_due_at NULL): theo quy tắc thu sau hoàn tất.
    // Booking tạo trước 2026-11-09 còn chờ trung tâm / caregiver xác nhận (luồng cũ vẫn chạy cho dữ liệu cũ): đưa booking vừa
    // tạo về đúng trạng thái đó. Chờ trung tâm → bỏ người hệ thống tự phân lúc tạo.
    void makeLegacyPending(String bookingId, BookingStatus status) {
        transactionTemplate.executeWithoutResult(tx -> {
            Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
            booking.setStatus(status);
            booking.setDepositDueAt(null);
            booking.setAcceptanceDueAt(Instant.now().plus(Duration.ofHours(24)));
            if (status == BookingStatus.PENDING_CENTER_CONFIRMATION) {
                booking.setCaregiverId(null);
                booking.setCaregiverUserId(null);
                bookingOccurrenceRepository.findAllByBooking(booking.getId()).forEach(BookingOccurrence::releaseCaregiver);
            }
            bookingRepository.save(booking);
        });
    }

    void makeLegacyPayment(String bookingId) {
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.setRemainingPaymentDueAt(null);
        bookingRepository.save(booking);
    }

    // Đặt hạn phần còn lại cách hiện tại một khoảng, xóa mốc đã nhắc.
    void setRemainingDue(UUID bookingId, Duration fromNow) {
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setRemainingPaymentDueAt(Instant.now().plus(fromNow));
        booking.setRemainingReminderMinutes(null);
        bookingRepository.save(booking);
    }

    void assertCaregiverAccess(UUID caregiverUserId, UUID recipientId, boolean expected) throws Exception {
        mockMvc.perform(get("/api/v1/bookings/caregiver-access")
                        .param("caregiverUserId", caregiverUserId.toString())
                        .param("recipientId", recipientId.toString())
                        .header("Authorization", serviceBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(expected));
    }

    // ─── 2026-11-10: caregiver vắng ca — cảnh báo sớm, hồ sơ vắng ca, giải trình, kết luận ───

    @Test
    void lateCheckInWarnsOnceThenMissedVisitOpensAnAbsenceTheCaregiverExplainsAndTheManagerConcludes() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");

        // Chưa tới giờ → chưa cảnh báo. Tới giờ (trong 30' check-in trễ) → cảnh báo đúng một lần cho caregiver, khách và
        // manager của trung tâm.
        assertThat(bookingOccurrenceService.findLateCheckInIds()).doesNotContain(first);
        moveStartAgo(first, 5);
        assertThat(bookingOccurrenceService.findLateCheckInIds()).contains(first);
        bookingOccurrenceService.warnLateCheckIn(first);
        bookingOccurrenceService.warnLateCheckIn(first);
        assertThat(bookingOccurrenceService.findLateCheckInIds()).doesNotContain(first);
        assertThat(notificationRepository.findAll())
                .filteredOn(notification -> first.equals(notification.getRefId())
                        && notification.getType() == NotificationType.CHECK_IN_LATE)
                .extracting(notification -> notification.getRecipientUserId())
                .hasSize(3)
                .contains(scenario.caregiver().userId(), scenario.customerId());

        // Quá hạn check-in → CAREGIVER_MISSED, hoàn tiền một lần, hồ sơ vắng ca PENDING (job chạy lại không ghi trùng).
        moveStartAgo(first, 40);
        bookingOccurrenceService.markMissed(first);
        bookingOccurrenceService.markMissed(first);
        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CAREGIVER_MISSED:" + first)::equals).hasSize(1);
        JsonNode absences = readResult(mockMvc.perform(get("/api/v1/caregiver-absences")
                        .header("Authorization", managerBearer(scenario)).param("bookingId", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.result.items[0].source").value("MISSED_CHECK_IN"))
                .andExpect(jsonPath("$.result.items[0].occurrenceId").value(first.toString()))
                .andExpect(jsonPath("$.result.items[0].caregiverId").value(scenario.caregiver().caregiverId().toString()))
                .andReturn());
        String absenceId = absences.get("items").get(0).get("id").asString();
        // Khách được báo tiền hoàn vào ví; caregiver được báo cần giải trình.
        assertThat(notificationRepository.findAll()).anyMatch(notification -> first.equals(notification.getRefId())
                && notification.getType() == NotificationType.OCCURRENCE_MISSED
                && notification.getRecipientUserId().equals(scenario.customerId())
                && notification.getBody().contains("hoàn vào ví"));
        assertThat(notificationRepository.findAll()).anyMatch(notification -> first.equals(notification.getRefId())
                && notification.getType() == NotificationType.CAREGIVER_ABSENCE_UPDATED
                && notification.getRecipientUserId().equals(scenario.caregiver().userId()));

        // Phạm vi: khách 403; caregiver khác / manager trung tâm khác 404; admin xem được nhưng không kết luận.
        mockMvc.perform(get("/api/v1/caregiver-absences").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isForbidden());
        String otherCaregiver = bearer(PLATFORM.addActiveCaregiver(scenario.centerId()).userId(), scenario.centerId(),
                "CAREGIVER");
        mockMvc.perform(get("/api/v1/caregiver-absences/" + absenceId).header("Authorization", otherCaregiver))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_ABSENCE_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/caregiver-absences").header("Authorization", otherCaregiver))
                .andExpect(jsonPath("$.result.items.length()").value(0));
        String otherManager = bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER");
        mockMvc.perform(get("/api/v1/caregiver-absences/" + absenceId).header("Authorization", otherManager))
                .andExpect(status().isNotFound());
        concludeAbsence(otherManager, absenceId, "CONFIRMED", null).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/caregiver-absences/" + absenceId).header("Authorization", adminBearer()))
                .andExpect(status().isOk());
        concludeAbsence(adminBearer(), absenceId, "CONFIRMED", null).andExpect(status().isForbidden());

        // Giải trình: quá ngắn 400, người khác 404, đúng người gửi (và sửa) được khi chưa kết luận.
        explainAbsence(caregiverBearer, absenceId, "ngắn")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ABSENCE_EXPLANATION_REQUIRED.getCode()));
        explainAbsence(otherCaregiver, absenceId, "Tôi bị tai nạn trên đường đến nhà khách").andExpect(status().isNotFound());
        explainAbsence(caregiverBearer, absenceId, "Tôi bị tai nạn trên đường đến nhà khách")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.explanation").value("Tôi bị tai nạn trên đường đến nhà khách"))
                .andExpect(jsonPath("$.result.explainedAt").exists());

        // Kết luận: PENDING không hợp lệ; miễn trừ cần ghi chú; kết luận một lần, sau đó khóa giải trình.
        concludeAbsence(managerBearer(scenario), absenceId, "PENDING", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ABSENCE_DECISION_REQUIRED.getCode()));
        concludeAbsence(managerBearer(scenario), absenceId, "EXCUSED", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.ABSENCE_CONCLUSION_NOTE_REQUIRED.getCode()));
        concludeAbsence(managerBearer(scenario), absenceId, "EXCUSED", "Có giấy xác nhận của bệnh viện")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("EXCUSED"))
                .andExpect(jsonPath("$.result.concludedAt").exists());
        concludeAbsence(managerBearer(scenario), absenceId, "CONFIRMED", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_ABSENCE_ALREADY_CONCLUDED.getCode()));
        explainAbsence(caregiverBearer, absenceId, "Bổ sung thêm giải trình sau khi đã kết luận")
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/caregiver-absences/statistics").header("Authorization", managerBearer(scenario))
                        .param("caregiverId", scenario.caregiver().caregiverId().toString()))
                .andExpect(jsonPath("$.result.excused").value(1))
                .andExpect(jsonPath("$.result.pending").value(0))
                .andExpect(jsonPath("$.result.total").value(1));
        mockMvc.perform(get("/api/v1/caregiver-absences").header("Authorization", caregiverBearer)
                        .param("status", "EXCUSED"))
                .andExpect(jsonPath("$.result.items.length()").value(1));
    }

    @Test
    void rejectedNoShowReportRecordsAnAbsenceAndTheManagerConfirmsIt() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();
        String caregiverBearer = bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER");
        moveStartAgo(occurrenceId, 10);
        reportNoShow(caregiverBearer, occurrenceId, "Khách không có nhà, đã gọi ba lần").andExpect(status().isOk());
        // Đã báo khách vắng thì không cảnh báo "chưa check-in" nữa.
        assertThat(bookingOccurrenceService.findLateCheckInIds()).doesNotContain(occurrenceId);
        decideNoShow(managerBearer(scenario), occurrenceId, false).andExpect(status().isOk());

        JsonNode absence = readResult(mockMvc.perform(get("/api/v1/caregiver-absences")
                        .header("Authorization", managerBearer(scenario)).param("status", "PENDING")
                        .param("bookingId", bookingId))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].source").value("NO_SHOW_REPORT_REJECTED"))
                .andReturn()).get("items").get(0);
        concludeAbsence(managerBearer(scenario), absence.get("id").asString(), "CONFIRMED", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
        mockMvc.perform(get("/api/v1/caregiver-absences/statistics").header("Authorization", adminBearer())
                        .param("centerId", scenario.centerId().toString()))
                .andExpect(jsonPath("$.result.confirmed").value(1));
    }

    // Trước 2026-11-10 khóa hoàn tiền của luồng này là một chuỗi cố định → chỉ lần đầu tiên trên toàn hệ thống được hoàn.
    @Test
    void rejectingAnEmergencyReplacementRefundsEveryVisitWithItsOwnKeyAndClosesTheBooking() throws Exception {
        Scenario scenario = newScenario();
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 2));
        acceptAndConfirm(scenario, bookingId, null);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId));
        UUID first = occurrences.get(0).getId();
        UUID second = occurrences.get(1).getId();
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency-reassignment")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("occurrenceIds", List.of(first.toString(), second.toString()),
                                "newCaregiverId", replacement.caregiverId().toString(), "reason", "Người cũ bị ốm"))))
                .andExpect(status().isOk());

        // Khách khác → 404; khách từ chối từng buổi → mỗi buổi một lệnh hoàn riêng; bấm lại → 409, không hoàn thêm.
        replacementConsent(bearer(UUID.randomUUID(), null, "CUSTOMER"), first, "REJECT_AND_CANCEL")
                .andExpect(status().isNotFound());
        replacementConsent(customerBearer(scenario), first, "REJECT_AND_CANCEL").andExpect(status().isOk());
        replacementConsent(customerBearer(scenario), first, "REJECT_AND_CANCEL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.REPLACEMENT_CONSENT_INVALID.getCode()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
        replacementConsent(customerBearer(scenario), second, "REJECT_AND_CANCEL").andExpect(status().isOk());

        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CENTER_CANCEL:" + first)::equals).hasSize(1);
        assertThat(PLATFORM.refundKeys()).filteredOn(("REFUND:CENTER_CANCEL:" + second)::equals).hasSize(1);
        BookingOccurrence cancelled = bookingOccurrenceRepository.findById(first).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(OccurrenceStatus.CANCELLED);
        assertThat(cancelled.getCancelledByParty()).isEqualTo(CancelledBy.CENTER);
        assertThat(cancelled.getCancelledAt()).isNotNull();
        // Không còn buổi nào → booking đóng, không treo CONFIRMED.
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
    }

    // 2026-11-10: người thay thế khẩn cấp phải rảnh — trước đó không kiểm, có thể giao một người hai nơi cùng giờ.
    @Test
    void emergencyReplacementRejectsACaregiverAlreadyBookedAtTheSameTime() throws Exception {
        Scenario scenario = newScenario();
        Scenario other = newScenarioInCenter(scenario.centerId());
        String bookingId = createAndGetId(scenario, dailyBody(scenario, scenario.caregiver().caregiverId(), 1));
        acceptAndConfirm(scenario, bookingId, null);
        // Người thay thế đã nhận một booking khác đúng khung giờ đó.
        String otherBookingId = createAndGetId(other, dailyBody(other, other.caregiver().caregiverId(), 1));
        acceptAndConfirm(other, otherBookingId, null);
        UUID occurrenceId = bookingOccurrenceRepository.findAllByBooking(UUID.fromString(bookingId)).getFirst().getId();

        emergencyReassign(scenario, bookingId, occurrenceId, other.caregiver().caregiverId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT.getCode()));
        assertThat(bookingOccurrenceRepository.findById(occurrenceId).orElseThrow().getCaregiverId())
                .isEqualTo(scenario.caregiver().caregiverId());

        // Người rảnh thì được; giao lại cho chính người đó → không còn buổi nào để thay.
        CaregiverIds free = PLATFORM.addActiveCaregiver(scenario.centerId());
        emergencyReassign(scenario, bookingId, occurrenceId, free.caregiverId()).andExpect(status().isOk());
        BookingOccurrence replaced = bookingOccurrenceRepository.findById(occurrenceId).orElseThrow();
        assertThat(replaced.getCaregiverId()).isEqualTo(free.caregiverId());
        assertThat(replaced.getCaregiverUserId()).isEqualTo(free.userId());
        assertThat(replaced.getReplacementConsentStatus()).hasToString("PENDING_CUSTOMER");
        emergencyReassign(scenario, bookingId, occurrenceId, free.caregiverId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.NO_REASSIGNABLE_OCCURRENCES.getCode()));
        // Manager trung tâm khác → 404.
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency-reassignment")
                        .header("Authorization", bearer(UUID.randomUUID(), PLATFORM.addCenter("ACTIVE"), "CENTER_MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("occurrenceIds", List.of(occurrenceId.toString()),
                                "newCaregiverId", free.caregiverId().toString(), "reason", "Ốm"))))
                .andExpect(status().isNotFound());
    }

    ResultActions emergencyReassign(Scenario scenario, String bookingId, UUID occurrenceId, UUID caregiverId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/emergency-reassignment")
                .header("Authorization", managerBearer(scenario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("occurrenceIds", List.of(occurrenceId.toString()),
                        "newCaregiverId", caregiverId.toString(), "reason", "Người cũ bị ốm"))));
    }

    ResultActions explainAbsence(String bearer, String absenceId, String explanation) throws Exception {
        return mockMvc.perform(post("/api/v1/caregiver-absences/" + absenceId + "/explanation")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("explanation", explanation))));
    }

    ResultActions concludeAbsence(String bearer, String absenceId, String decision, String note) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("decision", decision);
        body.put("note", note);
        return mockMvc.perform(post("/api/v1/caregiver-absences/" + absenceId + "/conclusion")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    ResultActions replacementConsent(String bearer, UUID occurrenceId, String action) throws Exception {
        return mockMvc.perform(post("/api/v1/booking-occurrences/" + occurrenceId + "/replacement-consent")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("action", action))));
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
