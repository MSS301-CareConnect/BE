package mss301.bookingservice;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.Cookie;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingStatusHistory;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingStatusHistoryRepository;
import mss301.bookingservice.service.BookingExpiryService;
import mss301.bookingservice.support.TestPlatformServer;
import mss301.bookingservice.support.TestPlatformServer.CaregiverIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    static final String BOOKING_INTERNAL_SECRET = "booking-internal-test-secret";
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
        registry.add("booking.identity.internal-secret", () -> TestPlatformServer.IDENTITY_SECRET);
        registry.add("booking.care-recipient.url", PLATFORM::baseUrl);
        registry.add("booking.care-recipient.internal-secret", () -> TestPlatformServer.CARE_RECIPIENT_SECRET);
        registry.add("booking.provider.url", PLATFORM::baseUrl);
        registry.add("booking.provider.internal-secret", () -> TestPlatformServer.PROVIDER_SECRET);
        registry.add("booking.internal-secret", () -> BOOKING_INTERNAL_SECRET);
        // Scheduler không chạy trong lúc test; test gọi BookingExpiryService trực tiếp.
        registry.add("booking.schedule.expiry-scan-interval", () -> "1h");
    }

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
    ObjectMapper objectMapper;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    @Autowired
    BookingRepository bookingRepository;

    @Autowired
    BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Autowired
    BookingExpiryService bookingExpiryService;

    @AfterEach
    void resetPlatform() {
        PLATFORM.setIdentityFailing(false);
        PLATFORM.setCareRecipientFailing(false);
        PLATFORM.setProviderFailing(false);
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===== Xác thực / phân quyền theo role =====

    @Test
    void missingOrInvalidTokenIs401() throws Exception {
        mockMvc.perform(get("/bookings"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id"));
        UUID userId = UUID.randomUUID();
        String refreshToken = PLATFORM.token(userId, null, List.of("careconnect-identity", "careconnect-api"), "refresh",
                List.of("CUSTOMER"));
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
        String identityOnlyToken = PLATFORM.token(userId, null, List.of("careconnect-identity"), "access", List.of("CUSTOMER"));
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + identityOnlyToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforcedPerApi() throws Exception {
        Scenario scenario = newScenario();
        mockMvc.perform(post("/bookings")
                        .header("Authorization", bearer(scenario.caregiver().userId(), scenario.centerId(), "CAREGIVER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/center/bookings").header("Authorization", bearer(scenario.customerId(), null, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/bookings").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/caregiver/occurrences").param("from", "2026-01-01").param("to", "2026-01-02")
                        .header("Authorization", bearer(scenario.customerId(), null, "CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    // ===== Tạo booking =====

    @Test
    void createWeeklyBookingGeneratesOccurrencesInUtcAndStoresSnapshot() throws Exception {
        Scenario scenario = newScenario();
        String requestId = "req-" + UUID.randomUUID();
        MvcResult result = mockMvc.perform(post("/bookings")
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
                .andExpect(jsonPath("$.result.services[0].serviceName").doesNotExist())
                .andExpect(jsonPath("$.result.totalAmount").doesNotExist())
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

        mockMvc.perform(get("/bookings/" + bookingId + "/occurrences").header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(6));
        mockMvc.perform(get("/bookings").header("Authorization", customerBearer(scenario)))
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
        mockMvc.perform(get("/bookings").header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void recipientAccessAndStatusAreChecked() throws Exception {
        Scenario scenario = newScenario();
        UUID stranger = PLATFORM.addUser("Người lạ", "0900000000");
        mockMvc.perform(post("/bookings")
                        .header("Authorization", bearer(stranger, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
        // VIEWER không đủ quyền đặt lịch (tối thiểu EDITOR) → vẫn 404, không lộ hồ sơ
        UUID viewer = PLATFORM.addUser("Người xem", "0900000001");
        PLATFORM.addRecipientManager(scenario.recipientId(), viewer, "VIEWER");
        mockMvc.perform(post("/bookings")
                        .header("Authorization", bearer(viewer, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isNotFound());
        UUID editor = PLATFORM.addUser("Người sửa", "0900000002");
        PLATFORM.addRecipientManager(scenario.recipientId(), editor, "EDITOR");
        mockMvc.perform(post("/bookings")
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
        mockMvc.perform(get("/bookings").header("Authorization", customerBearer(scenario)))
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
        mockMvc.perform(post("/center/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_REQUIRED.getCode()));
        mockMvc.perform(post("/center/bookings/" + bookingId + "/accept")
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
        mockMvc.perform(get("/caregiver/occurrences").param("from", from.toString()).param("to", to.toString())
                        .header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);

        mockMvc.perform(post("/internal/bookings/" + bookingId + "/deposit-paid")
                        .header("X-Internal-Secret", BOOKING_INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentReference", "PAY-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.confirmedAt").exists());
        // Gọi lại: idempotent
        mockMvc.perform(post("/internal/bookings/" + bookingId + "/deposit-paid")
                        .header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));

        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.result.occurrences.length()").value(6));

        MvcResult schedule = mockMvc.perform(get("/caregiver/occurrences").param("from", from.toString())
                        .param("to", to.toString())
                        .header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(6))
                .andExpect(jsonPath("$.result[0].recipientName").value("Bà Lan"))
                .andExpect(jsonPath("$.result[0].bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.result[0].recipientSnapshot").doesNotExist())
                .andReturn();
        String occurrenceId = readResult(schedule).get(0).get("id").asString();
        mockMvc.perform(get("/caregiver/occurrences/" + occurrenceId).header("Authorization", caregiverBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientSnapshot.allergies").value("Penicillin"))
                .andExpect(jsonPath("$.result.serviceAddress").exists());
        CaregiverIds otherCaregiver = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(get("/caregiver/occurrences/" + occurrenceId)
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
        mockMvc.perform(get("/center/bookings/" + bookingId).header("Authorization", otherManager))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_NOT_FOUND.getCode()));
        mockMvc.perform(post("/center/bookings/" + bookingId + "/reject")
                        .header("Authorization", otherManager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Không phải của tôi"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/center/bookings").header("Authorization", otherManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
        mockMvc.perform(get("/center/bookings")
                        .header("Authorization", bearer(UUID.randomUUID(), null, "CENTER_MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CENTER_SCOPE_REQUIRED.getCode()));
        mockMvc.perform(get("/center/bookings").header("Authorization", managerBearer(scenario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].customer.fullName").value("Khách Hàng"))
                .andExpect(jsonPath("$.result.items[0].recipientName").value("Bà Lan"));
        // Customer khác không thấy booking → 404
        UUID otherCustomer = PLATFORM.addUser("Khách khác", "0911111111");
        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", bearer(otherCustomer, null, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", bearer(otherCustomer, null, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Thử hủy"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectReleasesCaregiverAndCancelsOccurrences() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        mockMvc.perform(post("/center/bookings/" + bookingId + "/reject")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.REASON_REQUIRED.getCode()));
        mockMvc.perform(post("/center/bookings/" + bookingId + "/reject")
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
        mockMvc.perform(post("/center/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
    }

    @Test
    void customerCancelAndInvalidTransitions() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        mockMvc.perform(post("/internal/bookings/" + bookingId + "/deposit-paid")
                        .header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Đổi kế hoạch"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.cancelReason").value("Đổi kế hoạch"))
                .andExpect(jsonPath("$.result.cancelledBy").value(scenario.customerId().toString()))
                .andExpect(jsonPath("$.result.occurrences[5].status").value("CANCELLED"));
        mockMvc.perform(post("/bookings/" + bookingId + "/cancel")
                        .header("Authorization", customerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Hủy lần hai"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_BOOKING_TRANSITION.getCode()));
        mockMvc.perform(post("/center/bookings/" + bookingId + "/cancel")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Đã hủy rồi"))))
                .andExpect(status().isConflict());
    }

    @Test
    void managerChangesCaregiverForBookingAndSingleOccurrence() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0));
        acceptAndConfirm(scenario, bookingId, null);
        CaregiverIds replacement = PLATFORM.addActiveCaregiver(scenario.centerId());
        mockMvc.perform(patch("/center/bookings/" + bookingId + "/caregiver")
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
        String occurrenceId = readResult(mockMvc.perform(get("/bookings/" + bookingId + "/occurrences")
                        .header("Authorization", customerBearer(scenario)))
                .andReturn()).get(0).get("id").asString();
        mockMvc.perform(patch("/center/bookings/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiver().caregiverId().toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT.getCode()));
        mockMvc.perform(post("/center/bookings/" + busyBookingId + "/reject")
                        .header("Authorization", managerBearer(busy))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Giải phóng lịch"))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/center/bookings/occurrences/" + occurrenceId + "/caregiver")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("caregiverId", busy.caregiver().caregiverId().toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.caregiverId").value(busy.caregiver().caregiverId().toString()));
        // Caregiver của một buổi riêng cũng được xem hồ sơ người bệnh
        assertCaregiverAccess(busy.caregiver().userId(), scenario.recipientId(), true);
        mockMvc.perform(patch("/center/bookings/occurrences/" + occurrenceId + "/caregiver")
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
        mockMvc.perform(post("/center/bookings/" + bookingId + "/cancel")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "Caregiver ốm đột xuất"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"));
        assertCaregiverAccess(scenario.caregiver().userId(), scenario.recipientId(), false);
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
        mockMvc.perform(get("/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(jsonPath("$.result.status").value("EXPIRED"))
                .andExpect(jsonPath("$.result.occurrences[0].status").value("CANCELLED"));
        // Gọi lại không đổi gì; khung giờ đã được giải phóng
        bookingExpiryService.expire(booking.getId());
        createBooking(scenario, weeklyBody(scenario, scenario.caregiver().caregiverId(), 0), null)
                .andExpect(status().isOk());
        mockMvc.perform(post("/center/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario)))
                .andExpect(status().isConflict());
    }

    // ===== Admin / API nội bộ / hạ tầng =====

    @Test
    void adminSeesAllBookingsAndToleratesIdentityOutage() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        String adminBearer = bearer(UUID.randomUUID(), null, "ADMIN");
        mockMvc.perform(get("/admin/bookings").param("centerId", scenario.centerId().toString())
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].customer.fullName").value("Khách Hàng"));
        PLATFORM.setIdentityFailing(true);
        mockMvc.perform(get("/admin/bookings/" + bookingId).header("Authorization", adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(bookingId))
                .andExpect(jsonPath("$.result.customer").doesNotExist());
        mockMvc.perform(get("/admin/bookings").param("page", "-1").header("Authorization", adminBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGE.getCode()));
    }

    @Test
    void internalApiRequiresInternalSecret() throws Exception {
        Scenario scenario = newScenario();
        String bookingId = createAndGetId(scenario, weeklyBody(scenario, null, 0));
        mockMvc.perform(get("/internal/bookings/" + bookingId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/bookings/" + bookingId).header("X-Internal-Secret", "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/bookings/" + bookingId).header("Authorization", customerBearer(scenario)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/bookings/" + bookingId).header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(bookingId));
        String occurrenceId = readResult(mockMvc.perform(get("/bookings/" + bookingId + "/occurrences")
                .header("Authorization", customerBearer(scenario))).andReturn()).get(0).get("id").asString();
        mockMvc.perform(get("/internal/bookings/occurrences/" + occurrenceId).header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.bookingId").value(bookingId))
                .andExpect(jsonPath("$.result.recipientSnapshot.fullName").value("Bà Lan"));
        mockMvc.perform(get("/internal/bookings/" + UUID.randomUUID()).header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isNotFound());
    }

    @Test
    void cookieWriteRequiresCsrf() throws Exception {
        Scenario scenario = newScenario();
        String token = PLATFORM.accessToken(scenario.customerId(), null, "CUSTOMER");
        mockMvc.perform(post("/bookings")
                        .cookie(new Cookie("access_token", token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CSRF_INVALID.getCode()));
        mockMvc.perform(post("/bookings")
                        .cookie(new Cookie("access_token", token), new Cookie("XSRF-TOKEN", "csrf-value"))
                        .header("X-XSRF-TOKEN", "csrf-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(weeklyBody(scenario, null, 0))))
                .andExpect(status().isOk());
    }

    // ===== helpers =====

    record Scenario(UUID customerId, UUID recipientId, UUID centerId, CaregiverIds caregiver, UUID careServiceId) {
    }

    Scenario newScenario() {
        return newScenarioInCenter(PLATFORM.addCenter("ACTIVE"));
    }

    Scenario newScenarioInCenter(UUID centerId) {
        UUID customerId = PLATFORM.addUser("Khách Hàng", "0987654321");
        UUID recipientId = PLATFORM.addRecipient(customerId, "Bà Lan", "ACTIVE");
        CaregiverIds caregiver = PLATFORM.addActiveCaregiver(centerId);
        return new Scenario(customerId, recipientId, centerId, caregiver, UUID.randomUUID());
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
        body.put("services", List.of(Map.of("careServiceId", scenario.careServiceId().toString())));
        return body;
    }

    ResultActions createBooking(Scenario scenario, Map<String, Object> body, String idempotencyKey) throws Exception {
        var request = post("/bookings")
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
        mockMvc.perform(post("/center/bookings/" + bookingId + "/accept")
                        .header("Authorization", managerBearer(scenario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk());
        mockMvc.perform(post("/internal/bookings/" + bookingId + "/deposit-paid")
                        .header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CONFIRMED"));
    }

    void assertCaregiverAccess(UUID caregiverUserId, UUID recipientId, boolean expected) throws Exception {
        mockMvc.perform(get("/internal/bookings/caregiver-access")
                        .param("caregiverUserId", caregiverUserId.toString())
                        .param("recipientId", recipientId.toString())
                        .header("X-Internal-Secret", BOOKING_INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(expected));
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
