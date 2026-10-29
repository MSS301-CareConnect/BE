package mss301.carerecipientservice;

import mss301.carerecipientservice.repository.ManagerInvitationRepository;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.Cookie;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.support.TestBookingServer;
import mss301.carerecipientservice.support.TestIdentityServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.time.Instant;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test tích hợp qua MockMvc: JWT thật (ký RS256, JWKS từ TestIdentityServer), Feign thật tới IdentityService giả.
 * Mặc định H2; -Dcare-recipient.testing.postgres=true chạy trên PostgreSQL thật (schema/care_recipient.sql + validate).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CareRecipientApiTests {

    static final String PG_URL = "jdbc:postgresql://localhost:55495/postgres";
    static final String PG_USER = "postgres";
    static final String PG_PASSWORD = "care_recipient_test_only";
    static final TestIdentityServer IDENTITY = TestIdentityServer.get();
    static final TestBookingServer BOOKING = TestBookingServer.get();

    static {
        // Như CareRecipientServiceApplication.main: JVM chạy UTC (PostgreSQL 17 từ chối alias múi giờ cũ như Asia/Saigon).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        boolean postgres = Boolean.getBoolean("care-recipient.testing.postgres");
        String schema = "care_recipient_test_" + UUID.randomUUID().toString().replace("-", "");
        if (postgres) {
            createSchema(schema);
        }
        registry.add("spring.datasource.url", () -> postgres
                ? PG_URL + "?currentSchema=" + schema
                : "jdbc:h2:mem:care_recipient;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> postgres ? PG_USER : "sa");
        registry.add("spring.datasource.password", () -> postgres ? PG_PASSWORD : "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> postgres ? "validate" : "create-drop");
        registry.add("spring.sql.init.mode", () -> postgres ? "always" : "never");
        registry.add("spring.sql.init.schema-locations", () -> "file:./schema/care_recipient.sql");
        registry.add("care-recipient.jwt.issuer", () -> TestIdentityServer.ISSUER);
        registry.add("care-recipient.jwt.jwks-uri", IDENTITY::jwksUri);
        registry.add("care-recipient.identity.url", IDENTITY::baseUrl);
        registry.add("care-recipient.booking.url", BOOKING::baseUrl);
        registry.add("care-recipient.service-client.client-id", () -> TestIdentityServer.SERVICE_CLIENT_ID);
        registry.add("care-recipient.service-client.client-secret", () -> TestIdentityServer.SERVICE_CLIENT_SECRET);
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
    ManagerInvitationRepository managerInvitationRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    UUID owner;
    UUID relative;
    String relativeEmail;

    @BeforeEach
    void setUp() {
        owner = IDENTITY.addUser(uniqueEmail(), "Chủ hồ sơ", "ACTIVE", Set.of("CUSTOMER"));
        relativeEmail = uniqueEmail();
        relative = IDENTITY.addUser(relativeEmail, "Người thân", "ACTIVE", Set.of("CUSTOMER"));
    }

    @AfterEach
    void resetIdentity() {
        IDENTITY.setFailing(false);
        BOOKING.setFailing(false);
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===== Xác thực / phân quyền theo role =====

    @Test
    void missingTokenIs401WithEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/care-recipients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void tokenWithoutApiAudienceIsRejected() throws Exception {
        String identityOnlyToken = IDENTITY.token(owner, List.of("careconnect-identity"), "access",
                TestIdentityServer.ISSUER, List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", "Bearer " + identityOnlyToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenAndWrongIssuerAreRejected() throws Exception {
        String refreshToken = IDENTITY.token(owner, List.of("careconnect-identity", "careconnect-api"), "refresh",
                TestIdentityServer.ISSUER, List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
        String wrongIssuer = IDENTITY.token(owner, List.of("careconnect-api"), "access", "https://evil.test",
                List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", "Bearer " + wrongIssuer))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void caregiverCannotUseCustomerApi() throws Exception {
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", bearer(UUID.randomUUID(), "CAREGIVER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void forgedUserHeaderIsIgnored() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID stranger = UUID.randomUUID();
        // X-User-Id do client tự gửi không được tin — danh tính lấy từ JWT.
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId)
                        .header("Authorization", bearer(stranger, "CUSTOMER"))
                        .header("X-User-Id", owner.toString()))
                .andExpect(status().isNotFound());
    }

    // ===== Hồ sơ =====

    @Test
    void creatorBecomesPrimaryOwnerAndSeesProfile() throws Exception {
        UUID recipientId = createRecipient(owner);
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.myAccessLevel").value("OWNER"))
                .andExpect(jsonPath("$.result.profile.bloodType").value("O+"))
                .andExpect(jsonPath("$.result.profile.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.profile.createdBy").value(owner.toString()))
                .andExpect(jsonPath("$.result.medications.length()").value(1))
                .andExpect(jsonPath("$.result.medications[0].timeOfDay[1]").value("20:00"))
                .andExpect(jsonPath("$.result.emergencyContacts[0].primary").value(true));
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].myAccessLevel").value("OWNER"))
                .andExpect(jsonPath("$.result.items[0].primaryManager").value(true));
    }

    @Test
    void strangerGets404NotForbidden() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID stranger = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(stranger, "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(stranger, "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByKeywordAndStatus() throws Exception {
        createRecipient(owner, "Nguyễn Văn Bố");
        UUID second = createRecipient(owner, "Trần Thị Mẹ");
        mockMvc.perform(patch("/api/v1/care-recipients/" + second + "/status").param("status", "INACTIVE")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients").param("keyword", "mẹ").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].fullName").value("Trần Thị Mẹ"));
        mockMvc.perform(get("/api/v1/care-recipients").param("status", "ACTIVE").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].fullName").value("Nguyễn Văn Bố"));
        mockMvc.perform(get("/api/v1/care-recipients").param("size", "101").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGE.getCode()));
    }

    @Test
    void updateChecksVersionAndArchivedStatusIsNotSettable() throws Exception {
        UUID recipientId = createRecipient(owner);
        String update = """
                {"fullName":"Tên mới","version":0,"allergies":"Penicillin"}
                """;
        mockMvc.perform(put("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.fullName").value("Tên mới"))
                .andExpect(jsonPath("$.result.version").value(1))
                .andExpect(jsonPath("$.result.updatedBy").value(owner.toString()));
        // Gửi lại version cũ → xung đột.
        mockMvc.perform(put("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_VERSION_CONFLICT.getCode()));
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/status").param("status", "ARCHIVED")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_STATUS.getCode()));
    }

    @Test
    void createValidatesInputAndRejectsServerManagedFields() throws Exception {
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"relationshipType\":\"CHILD\",\"latitude\":10.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_COORDINATES.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"relationshipType\":\"CHILD\",\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_REQUEST.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"relationshipType\":\"CHILD\",\"bloodType\":\"Z+\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relationshipType\":\"CHILD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.FULLNAME_REQUIRED.getCode()));
    }

    // M-07: còn booking chưa kết thúc → không tạm ngưng / lưu trữ hồ sơ; BookingService lỗi → 503.
    @Test
    void recipientWithUnfinishedBookingsCannotBeSuspendedOrArchived() throws Exception {
        UUID recipientId = createRecipient(owner);
        BOOKING.setUnfinishedBookings(recipientId, 2);
        try {
            mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/status").param("status", "INACTIVE")
                            .header("Authorization", bearer(owner, "CUSTOMER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_HAS_ACTIVE_BOOKINGS.getCode()));
            mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_HAS_ACTIVE_BOOKINGS.getCode()));
            BOOKING.setFailing(true);
            mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                    .andExpect(status().isServiceUnavailable());
        } finally {
            BOOKING.setFailing(false);
            BOOKING.setUnfinishedBookings(recipientId, 0);
        }
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.profile.status").value("ACTIVE"));
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/status").param("status", "INACTIVE")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void archiveHidesFromCustomerButAdminCanStillRead() throws Exception {
        UUID recipientId = createRecipient(owner);
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isNotFound());
        UUID admin = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profile.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.result.myAccessLevel").doesNotExist())
                .andExpect(jsonPath("$.result.careSnapshot").doesNotExist());
        mockMvc.perform(get("/api/v1/care-recipients").param("includeDeleted", "true").param("createdBy", owner.toString())
                        .header("Authorization", bearer(admin, "ADMIN")))
                .andExpect(jsonPath("$.result.totalElements").value(1));
        mockMvc.perform(get("/api/v1/care-recipients").param("createdBy", owner.toString())
                        .header("Authorization", bearer(admin, "ADMIN")))
                .andExpect(jsonPath("$.result.totalElements").value(0));
        // CUSTOMER gửi bộ lọc chỉ dành cho ADMIN → bị bỏ qua, vẫn chỉ thấy hồ sơ mình quản lý (đã lưu trữ thì không thấy).
        mockMvc.perform(get("/api/v1/care-recipients").param("includeDeleted", "true").param("createdBy", owner.toString())
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void adminListSeesAllWhileCustomerListSeesOnlyOwn() throws Exception {
        UUID ownRecipient = createRecipient(owner, "Hồ sơ của chủ");
        UUID otherRecipient = createRecipient(relative, "Hồ sơ của người khác");
        UUID admin = UUID.randomUUID();
        // ADMIN: thấy hồ sơ của cả hai khách hàng (lọc theo người tạo vì DB test dùng chung giữa các test).
        mockMvc.perform(get("/api/v1/care-recipients").param("createdBy", owner.toString())
                        .header("Authorization", bearer(admin, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(ownRecipient.toString()))
                .andExpect(jsonPath("$.result.items[0].myAccessLevel").doesNotExist());
        mockMvc.perform(get("/api/v1/care-recipients").param("createdBy", relative.toString())
                        .header("Authorization", bearer(admin, "ADMIN")))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(otherRecipient.toString()));
        // CUSTOMER: chỉ hồ sơ mình quản lý, kể cả khi cố lọc theo người tạo khác.
        mockMvc.perform(get("/api/v1/care-recipients").param("createdBy", relative.toString())
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].id").value(ownRecipient.toString()))
                .andExpect(jsonPath("$.result.items[0].myAccessLevel").value("OWNER"));
        // CUSTOMER xem chi tiết hồ sơ người khác → 404 (không lộ sự tồn tại).
        mockMvc.perform(get("/api/v1/care-recipients/" + otherRecipient).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
        // CENTER_MANAGER không có API danh sách / chi tiết hồ sơ.
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", bearer(UUID.randomUUID(), "CENTER_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleSpecificPathsNoLongerExist() throws Exception {
        UUID recipientId = createRecipient(owner);
        String adminToken = bearer(UUID.randomUUID(), "ADMIN");
        // "/admin" giờ khớp /{id} với id không phải UUID → 400, không còn là danh sách admin.
        mockMvc.perform(get("/api/v1/care-recipients/admin").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/care-recipients/admin/" + recipientId).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        UUID caregiver = UUID.randomUUID();
        BOOKING.assign(caregiver, recipientId);
        mockMvc.perform(get("/api/v1/care-recipients/caregiver/" + recipientId)
                        .header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isNotFound());
    }

    // ===== Thuốc / liên hệ khẩn cấp =====

    @Test
    void medicationLifecycleAndDateValidation() throws Exception {
        UUID recipientId = createRecipient(owner);
        String medication = """
                {"medicationName":"Metformin","dosage":"500mg","frequency":"2 lần/ngày","timeOfDay":["07:00","19:00"],
                 "route":"ORAL","startDate":"2026-01-01"}
                """;
        JsonNode created = readResult(mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(medication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.active").value(true))
                .andReturn());
        String medicationId = created.get("id").asString();
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/medications/" + medicationId + "/active")
                        .param("active", "false").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.active").value(false));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/medications").param("active", "true")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.length()").value(1));
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationName\":\"X\",\"startDate\":\"2026-02-01\",\"endDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_MEDICATION_DATES.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationName\":\"X\",\"startDate\":\"2026-02-01\",\"timeOfDay\":[\"25:00\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_TIME_OF_DAY.getCode()));
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/medications/" + medicationId)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/medications/" + medicationId)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.MEDICATION_NOT_FOUND.getCode()));
    }

    @Test
    void onlyOnePrimaryEmergencyContact() throws Exception {
        UUID recipientId = createRecipient(owner);
        JsonNode second = readResult(mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/emergency-contacts")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Hàng xóm\",\"relationship\":\"Hàng xóm\",\"phoneNumber\":\"0911111111\",\"priority\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.primary").value(false))
                .andReturn());
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/emergency-contacts/" + second.get("id").asString() + "/primary")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.primary").value(true));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/emergency-contacts")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].fullName").value("Hàng xóm"))
                .andExpect(jsonPath("$.result[0].primary").value(true))
                .andExpect(jsonPath("$.result[1].primary").value(false));
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A","relationshipType":"CHILD","emergencyContacts":[
                                 {"fullName":"B","relationship":"Con","phoneNumber":"0900000001","primary":true},
                                 {"fullName":"C","relationship":"Con","phoneNumber":"0900000002","primary":true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.DUPLICATE_PRIMARY_CONTACT.getCode()));
    }

    // ===== Người thân & phân quyền OWNER / EDITOR / VIEWER =====

    @Test
    void viewerCanReadButNotWriteAndEditorCanEdit() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID managerId = addManager(recipientId, relativeEmail, "VIEWER");
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.myAccessLevel").value("VIEWER"));
        mockMvc.perform(put("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"X\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_ACCESS_DENIED.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"medicationName\":\"X\",\"startDate\":\"2026-02-01\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/managers/" + managerId)
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accessLevel\":\"EDITOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessLevel").value("EDITOR"));
        mockMvc.perform(put("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Sửa bởi editor\",\"version\":0}"))
                .andExpect(status().isOk());
        // EDITOR không quản lý danh sách người thân và không lưu trữ hồ sơ.
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managersListIncludesIdentityProfilesFromOneBatchCall() throws Exception {
        UUID recipientId = createRecipient(owner);
        addManager(recipientId, relativeEmail, "EDITOR");
        int batchCallsBefore = IDENTITY.batchCalls();
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/managers")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .header("X-Request-Id", "req-managers-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].primary").value(true))
                .andExpect(jsonPath("$.result[0].fullName").value("Chủ hồ sơ"))
                .andExpect(jsonPath("$.result[1].email").value(relativeEmail.toLowerCase(Locale.ROOT)))
                .andExpect(jsonPath("$.result[1].profileAvailable").value(true));
        assertThat(IDENTITY.batchCalls() - batchCallsBefore).isEqualTo(1);
        assertThat(IDENTITY.lastRequestId()).isEqualTo("req-managers-12345");
    }

    @Test
    void managersListStillWorksWhenIdentityIsDown() throws Exception {
        UUID recipientId = createRecipient(owner);
        addManager(recipientId, relativeEmail, "VIEWER");
        IDENTITY.setFailing(true);
        // Đủ lỗi để mở circuit — lời gọi sau không tới Identity nữa nhưng API vẫn trả danh sách.
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/managers")
                            .header("Authorization", bearer(owner, "CUSTOMER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.length()").value(2))
                    .andExpect(jsonPath("$.result[0].profileAvailable").value(false))
                    .andExpect(jsonPath("$.result[0].fullName").doesNotExist())
                    .andExpect(jsonPath("$.result[0].accessLevel").value("OWNER"));
        }
        boolean anyOpen = circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().stream()
                .anyMatch(circuitBreaker -> circuitBreaker.getState() == CircuitBreaker.State.OPEN);
        assertThat(anyOpen).isTrue();
        // Mời người thân cần tra Identity → không đoán, trả 503.
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.IDENTITY_UNAVAILABLE.getCode()));
    }

    // C-04: lời mời trả cùng một hình dạng cho mọi email — không lộ tài khoản tồn tại / bị khóa / không phải khách,
    // không có tên hay SĐT của người được mời; người không đúng email không chấp nhận được.
    @Test
    void invitationNeverRevealsWhetherTheEmailBelongsToAnAccount() throws Exception {
        UUID recipientId = createRecipient(owner);
        String lockedEmail = uniqueEmail();
        IDENTITY.addUser(lockedEmail, "Bị khóa", "LOCKED", Set.of("CUSTOMER"));
        String caregiverEmail = uniqueEmail();
        IDENTITY.addUser(caregiverEmail, "Caregiver", "ACTIVE", Set.of("CAREGIVER"));
        for (String email : List.of(lockedEmail, caregiverEmail, uniqueEmail(), relativeEmail)) {
            mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                            .header("Authorization", bearer(owner, "CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.status").value("PENDING"))
                    .andExpect(jsonPath("$.result.email").value(email.toLowerCase(Locale.ROOT)))
                    .andExpect(jsonPath("$.result.fullName").doesNotExist())
                    .andExpect(jsonPath("$.result.phoneNumber").doesNotExist())
                    .andExpect(jsonPath("$.result.userId").doesNotExist());
        }
        // Chưa chấp nhận → người thân chưa thấy hồ sơ, chủ hồ sơ chưa thấy người thân trong danh sách quản lý.
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/managers").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result.length()").value(1));
        // Lời mời đang chờ cho cùng email → 409, không tạo trùng.
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + relativeEmail.toUpperCase(Locale.ROOT) + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVITATION_EXISTED.getCode()));
        // Người được mời thấy đúng một lời mời của mình; người khác không thấy và không chấp nhận được.
        JsonNode mine = readResult(mockMvc.perform(get("/api/v1/care-recipients/invitations/me")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].recipientFullName").exists())
                .andReturn());
        String invitationId = mine.get(0).get("id").asString();
        UUID stranger = IDENTITY.addUser(uniqueEmail(), "Người lạ", "ACTIVE", Set.of("CUSTOMER"));
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(stranger, "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVITATION_NOT_FOUND.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessLevel").value("VIEWER"));
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVITATION_NOT_PENDING.getCode()));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk());
        // Đã là người quản lý → mời lại email đó báo đã quản lý.
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + relativeEmail + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.MANAGER_EXISTED.getCode()));
        // Booking chỉ được báo cho người có tài khoản (người thân), không cho email lạ / bị khóa.
        assertThat(BOOKING.notifications()).anyMatch(body -> body.contains(relative.toString())
                && body.contains("RECIPIENT_INVITATION"));
    }

    @Test
    void directManagerAddIsGone() throws Exception {
        UUID recipientId = createRecipient(owner);
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/managers")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + relativeEmail + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invitationCanBeDeclinedRevokedAndExpires() throws Exception {
        UUID recipientId = createRecipient(owner);
        String invitationId = invite(recipientId, relativeEmail, "EDITOR");
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/decline")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/invitations/me").header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(jsonPath("$.result.length()").value(0));

        String second = invite(recipientId, relativeEmail, "EDITOR");
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/invitations/" + second)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + second + "/accept")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isConflict());

        // Hết hạn sau 7 ngày (giả lập bằng cách lùi hạn trong DB).
        String third = invite(recipientId, relativeEmail, "EDITOR");
        transactionTemplate.executeWithoutResult(status -> managerInvitationRepository.findById(UUID.fromString(third))
                .orElseThrow().setExpiresAt(Instant.now().minusSeconds(60)));
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + third + "/accept")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVITATION_EXPIRED.getCode()));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/invitations").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.result[?(@.status == 'DECLINED')]").exists())
                .andExpect(jsonPath("$.result[?(@.status == 'REVOKED')]").exists());
        // Hết hạn thì mời lại được.
        invite(recipientId, relativeEmail, "EDITOR");
    }

    @Test
    void onlyPrimaryOwnerInvitesOrRemovesOthers() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID relativeManagerId = addManager(recipientId, relativeEmail, "OWNER");
        // OWNER không phải quản lý chính: không mời, không gỡ người khác (D19).
        mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"relationshipType\":\"CHILD\",\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRIMARY_OWNER_REQUIRED.getCode()));
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + findManagerId(recipientId, owner))
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + relativeManagerId)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
    }

    @Test
    void primaryAndLastOwnerAreProtected() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID ownerManagerId = findManagerId(recipientId, owner);
        UUID relativeManagerId = addManager(recipientId, relativeEmail, "EDITOR");

        // Quản lý chính (cũng là OWNER cuối cùng) không tự rời / hạ quyền được.
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + ownerManagerId)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRIMARY_TRANSFER_REQUIRED.getCode()));
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/managers/" + ownerManagerId)
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accessLevel\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
        // Chỉ chuyển quản lý chính cho OWNER.
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/managers/" + relativeManagerId + "/primary")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PRIMARY_MUST_BE_OWNER.getCode()));
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/managers/" + relativeManagerId)
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accessLevel\":\"OWNER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/care-recipients/" + recipientId + "/managers/" + relativeManagerId + "/primary")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.primary").value(true));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/managers").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(jsonPath("$.result[?(@.primary == true)].userId").value(relative.toString()));
        // Người cũ giờ không còn là quản lý chính → tự rời được (vẫn còn OWNER khác).
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + ownerManagerId)
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerCanLeaveButCannotRemoveOthers() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID ownerManagerId = findManagerId(recipientId, owner);
        UUID relativeManagerId = addManager(recipientId, relativeEmail, "VIEWER");
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + ownerManagerId)
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId + "/managers/" + relativeManagerId)
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Bạn đã rời khỏi hồ sơ"));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isNotFound());
    }

    // ===== CSRF cho web (cookie) =====

    @Test
    void cookieAuthenticatedWriteNeedsCsrfButBearerDoesNot() throws Exception {
        String accessToken = IDENTITY.accessToken(owner, "CUSTOMER");
        String body = "{\"fullName\":\"Qua cookie\",\"relationshipType\":\"CHILD\"}";
        mockMvc.perform(post("/api/v1/care-recipients").cookie(new Cookie("access_token", accessToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.CSRF_INVALID.getCode()));
        mockMvc.perform(post("/api/v1/care-recipients")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("XSRF-TOKEN", "csrf-value"))
                        .header("X-XSRF-TOKEN", "other-value")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        MvcResult accepted = mockMvc.perform(post("/api/v1/care-recipients")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("XSRF-TOKEN", "csrf-value"))
                        .header("X-XSRF-TOKEN", "csrf-value")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        // Service không bao giờ ghi cookie (CSRF/token do IdentityService quản lý).
        assertThat(accepted.getResponse().getHeaders("Set-Cookie")).isEmpty();
        mockMvc.perform(post("/api/v1/care-recipients").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    // ===== API liên service =====

    @Test
    void interServiceEndpointsRequireAuthAndReturnData() throws Exception {
        UUID recipientId = createRecipient(owner);
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/snapshot"))
                .andExpect(status().isUnauthorized());
        // Chủ hồ sơ được xem snapshot; access vẫn chỉ dành cho SERVICE.
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/snapshot")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", owner.toString())
                        .header("Authorization", bearer(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/snapshot")
                        .header("Authorization", bearer(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientId").value(recipientId.toString()))
                .andExpect(jsonPath("$.result.age").isNumber())
                .andExpect(jsonPath("$.result.allergies").value("Hải sản"))
                .andExpect(jsonPath("$.result.activeMedications.length()").value(1))
                .andExpect(jsonPath("$.result.primaryEmergencyContact.fullName").value("Con gái"))
                .andExpect(jsonPath("$.result.medicalHistory").doesNotExist());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", owner.toString()).param("minLevel", "OWNER")
                        .header("Authorization", bearer(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(true));
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", UUID.randomUUID().toString())
                        .header("Authorization", bearer(UUID.randomUUID(), "SERVICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(false));
        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[\"" + recipientId + "\",\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1));
        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"recipientIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_IDS_REQUIRED.getCode()));
    }

    @Test
    void openApiDocumentsSecuritySchemes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/care-recipients/batch'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/care-recipients'].post.security[0].csrf").exists())
                .andExpect(jsonPath("$.paths['/api/v1/care-recipients'].get.parameters[?(@.name == 'actorId')]").isEmpty());
    }

    @Test
    void errorCodesAreUnique() {
        Set<Integer> codes = new HashSet<>();
        for (ErrorCode errorCode : ErrorCode.values()) {
            assertThat(codes.add(errorCode.getCode())).as(errorCode.name()).isTrue();
        }
        boolean allInRange = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .allMatch(code -> code == 401 || code == 403 || code == 404 || code == 9999 || (code >= 2000 && code <= 2999));
        assertThat(allInRange).isTrue();
    }

    // ===== Caregiver xem hồ sơ khi BookingService xác nhận phân công =====

    @Test
    void assignedCaregiverSeesCareSnapshotOnly() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID caregiver = UUID.randomUUID();
        BOOKING.assign(caregiver, recipientId);
        int rejectedBefore = BOOKING.rejectedUserTokens();
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.careSnapshot.recipientId").value(recipientId.toString()))
                .andExpect(jsonPath("$.result.careSnapshot.allergies").value("Hải sản"))
                .andExpect(jsonPath("$.result.careSnapshot.activeMedications[0].medicationName").value("Insulin"))
                .andExpect(jsonPath("$.result.careSnapshot.primaryEmergencyContact.fullName").value("Con gái"))
                .andExpect(jsonPath("$.result.careSnapshot.medicalHistory").doesNotExist())
                // Không lộ nhiều hơn snapshot: không profile đầy đủ, không danh sách thuốc / liên hệ / quyền.
                .andExpect(jsonPath("$.result.profile").doesNotExist())
                .andExpect(jsonPath("$.result.medications").doesNotExist())
                .andExpect(jsonPath("$.result.emergencyContacts").doesNotExist())
                .andExpect(jsonPath("$.result.myAccessLevel").doesNotExist());
        // Booking chỉ nhận token SERVICE: không lần nào token caregiver bị chuyển tiếp sang Booking.
        assertThat(BOOKING.rejectedUserTokens()).isEqualTo(rejectedBefore);
        // Gỡ phân công (booking hết hiệu lực) → không xem được nữa
        BOOKING.unassign(caregiver, recipientId);
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
    }

    @Test
    void unassignedCaregiverGets404AndCaregiverCannotList() throws Exception {
        UUID recipientId = createRecipient(owner);
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId)
                        .header("Authorization", bearer(UUID.randomUUID(), "CAREGIVER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
        // Caregiver không có API danh sách và không gọi được API con (thuốc) dù được phân công
        UUID caregiver = UUID.randomUUID();
        BOOKING.assign(caregiver, recipientId);
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/medications")
                        .header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void archivedRecipientIsNotVisibleEvenWhenAssigned() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID caregiver = UUID.randomUUID();
        BOOKING.assign(caregiver, recipientId);
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void bookingOutageFailsClosedWith503() throws Exception {
        UUID recipientId = createRecipient(owner);
        UUID caregiver = UUID.randomUUID();
        BOOKING.assign(caregiver, recipientId);
        BOOKING.setFailing(true);
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId).header("Authorization", bearer(caregiver, "CAREGIVER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.BOOKING_UNAVAILABLE.getCode()));
    }

    @Test
    void snapshotAndBatchOnlyExposeManagedRecipients() throws Exception {
        UUID ownId = createRecipient(owner);
        UUID otherId = createRecipient(relative);
        mockMvc.perform(get("/api/v1/care-recipients/" + ownId + "/snapshot")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/care-recipients/" + ownId + "/snapshot")
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/care-recipients/" + ownId + "/access").param("userId", owner.toString())
                        .header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("recipientIds", List.of(ownId, otherId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(ownId.toString()));
    }

    @Test
    void inactiveRecipientBlocksEditorAndAllChildWritesUntilOwnerReactivates() throws Exception {
        UUID id = createRecipient(owner);
        addManager(id, relativeEmail, "EDITOR");
        JsonNode inactive = readResult(mockMvc.perform(patch("/api/v1/care-recipients/" + id + "/status")
                        .param("status", "INACTIVE").header("Authorization", bearer(owner, "CUSTOMER")))
                .andExpect(status().isOk()).andReturn());
        String update = objectMapper.writeValueAsString(Map.of("fullName", "Tên đã sửa", "version", inactive.path("version").asLong()));
        mockMvc.perform(put("/api/v1/care-recipients/" + id).header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_INACTIVE.getCode()));
        mockMvc.perform(put("/api/v1/care-recipients/" + id).header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        String medication = "{\"medicationName\":\"Vitamin C\",\"route\":\"ORAL\",\"startDate\":\"2026-01-01\"}";
        mockMvc.perform(post("/api/v1/care-recipients/" + id + "/medications").header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(medication))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_INACTIVE.getCode()));
        mockMvc.perform(patch("/api/v1/care-recipients/" + id + "/status").param("status", "ACTIVE")
                        .header("Authorization", bearer(owner, "CUSTOMER"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/care-recipients/" + id + "/medications").header("Authorization", bearer(relative, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(medication)).andExpect(status().isOk());
    }

    // ===== Helpers =====

    UUID createRecipient(UUID userId) throws Exception {
        return createRecipient(userId, "Nguyễn Văn Ông");
    }

    UUID createRecipient(UUID userId, String fullName) throws Exception {
        String body = """
                {"fullName":"%s","dateOfBirth":"1945-05-20","gender":"MALE","bloodType":"O+",
                 "addressLine":"12 Lê Lợi","district":"Quận 1","province":"TP.HCM","latitude":10.776889,"longitude":106.700806,
                 "allergies":"Hải sản","medicalHistory":"Tiểu đường type 2","mobilityLevel":"NEEDS_ASSISTANCE",
                 "relationshipType":"CHILD",
                 "emergencyContacts":[{"fullName":"Con gái","relationship":"Con","phoneNumber":"0909999999","primary":true}],
                 "medications":[{"medicationName":"Insulin","route":"INJECTION","startDate":"2026-01-01","timeOfDay":["07:00","20:00"]}]}
                """.formatted(fullName);
        JsonNode result = readResult(mockMvc.perform(post("/api/v1/care-recipients")
                        .header("Authorization", bearer(userId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn());
        return UUID.fromString(result.get("profile").get("id").asString());
    }

    String invite(UUID recipientId, String email, String accessLevel) throws Exception {
        JsonNode result = readResult(mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", bearer(owner, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "relationshipType", "CHILD", "accessLevel", accessLevel))))
                .andExpect(status().isOk())
                .andReturn());
        return result.get("id").asString();
    }

    // Người thân (relative) được mời rồi tự chấp nhận — cách duy nhất để trở thành người quản lý (C-04).
    UUID addManager(UUID recipientId, String email, String accessLevel) throws Exception {
        String invitationId = invite(recipientId, email, accessLevel);
        JsonNode result = readResult(mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(relative, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.primary").value(false))
                .andExpect(jsonPath("$.result.profileAvailable").value(true))
                .andReturn());
        return UUID.fromString(result.get("id").asString());
    }

    UUID findManagerId(UUID recipientId, UUID userId) throws Exception {
        JsonNode managers = readResult(mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/managers")
                        .header("Authorization", bearer(userId, "CUSTOMER")))
                .andReturn());
        for (JsonNode manager : managers) {
            if (manager.get("userId").asString().equals(userId.toString())) {
                return UUID.fromString(manager.get("id").asString());
            }
        }
        throw new IllegalStateException("Manager not found");
    }

    JsonNode readResult(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("result");
    }


    String bearer(UUID userId, String... roles) {
        return "Bearer " + IDENTITY.accessToken(userId, roles);
    }

    String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }
}
