package mss301.carerecipientservice;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.RecipientBatchRequest;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CareRecipientInterServiceEndpointsTests {

    static final TestIdentityServer IDENTITY = TestIdentityServer.get();
    static final TestBookingServer BOOKING = TestBookingServer.get();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:care_recipient_interservice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("care-recipient.jwt.issuer", () -> TestIdentityServer.ISSUER);
        registry.add("care-recipient.jwt.jwks-uri", IDENTITY::jwksUri);
        registry.add("care-recipient.identity.url", IDENTITY::baseUrl);
        registry.add("care-recipient.booking.url", BOOKING::baseUrl);
        registry.add("care-recipient.service-client.client-id", () -> TestIdentityServer.SERVICE_CLIENT_ID);
        registry.add("care-recipient.service-client.client-secret", () -> TestIdentityServer.SERVICE_CLIENT_SECRET);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    Resilience4JCircuitBreakerFactory circuitBreakerFactory;

    UUID ownerId;
    UUID viewerId;
    String callerToken;
    String serviceToken;

    @BeforeEach
    void setUp() {
        ownerId = IDENTITY.addUser("owner-" + UUID.randomUUID() + "@example.test", "Owner User", "ACTIVE", Set.of("CUSTOMER"));
        viewerId = IDENTITY.addUser("viewer-" + UUID.randomUUID() + "@example.test", "Viewer User", "ACTIVE", Set.of("CUSTOMER"));
        callerToken = "Bearer " + IDENTITY.accessToken(ownerId, "CUSTOMER");
        serviceToken = "Bearer " + IDENTITY.accessToken(UUID.randomUUID(), "SERVICE");
    }

    @AfterEach
    void cleanup() {
        IDENTITY.setFailing(false);
        BOOKING.setFailing(false);
        circuitBreakerFactory.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // ===== 1. GET /api/v1/care-recipients/{id}/snapshot =====

    @Test
    void snapshot_unauthenticated_returns401() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/care-recipients/" + randomId + "/snapshot"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void snapshot_nonExistentRecipient_returns404() throws Exception {
        UUID nonExistent = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/care-recipients/" + nonExistent + "/snapshot")
                        .header("Authorization", serviceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
    }

    @Test
    void snapshot_softDeletedRecipient_returns404() throws Exception {
        UUID recipientId = createRecipient(ownerId, "Bà Xóa Mềm");
        // Soft delete the recipient
        mockMvc.perform(delete("/api/v1/care-recipients/" + recipientId)
                        .header("Authorization", callerToken))
                .andExpect(status().isOk());

        // Snapshot should return 404 for deleted recipient
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/snapshot")
                        .header("Authorization", serviceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_NOT_FOUND.getCode()));
    }

    @Test
    void snapshot_validRecipient_returnsExpectedDataWithoutMedicalHistory() throws Exception {
        UUID recipientId = createRecipient(ownerId, "Cụ Nguyễn Văn An");

        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/snapshot")
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recipientId").value(recipientId.toString()))
                .andExpect(jsonPath("$.result.fullName").value("Cụ Nguyễn Văn An"))
                .andExpect(jsonPath("$.result.allergies").value("Hải sản"))
                .andExpect(jsonPath("$.result.primaryEmergencyContact.fullName").value("Người Liên Hệ"))
                .andExpect(jsonPath("$.result.activeMedications.length()").value(1))
                .andExpect(jsonPath("$.result.medicalHistory").doesNotExist())
                .andExpect(jsonPath("$.result.snapshotAt").exists());
    }

    // ===== 2. GET /api/v1/care-recipients/{id}/access =====

    @Test
    void access_unauthenticated_returns401() throws Exception {
        UUID randomId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/care-recipients/" + randomId + "/access")
                        .param("userId", ownerId.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void access_ownerUser_returnsAllowedTrueForOwnerAndViewer() throws Exception {
        UUID recipientId = createRecipient(ownerId, "Bác Trần");

        // Check OWNER access level
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", ownerId.toString())
                        .param("minLevel", "OWNER")
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(true))
                .andExpect(jsonPath("$.result.accessLevel").value("OWNER"));

        // Check VIEWER access level (owner covers VIEWER)
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", ownerId.toString())
                        .param("minLevel", "VIEWER")
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(true));
    }

    @Test
    void access_viewerUser_returnsAllowedFalseForOwnerLevel() throws Exception {
        UUID recipientId = createRecipient(ownerId, "Bác Lê");
        UUID actualViewerUserId = addManager(recipientId, "VIEWER");

        // Viewer should NOT have OWNER level
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", actualViewerUserId.toString())
                        .param("minLevel", "OWNER")
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(false))
                .andExpect(jsonPath("$.result.accessLevel").value("VIEWER"));

        // Viewer SHOULD have VIEWER level
        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", actualViewerUserId.toString())
                        .param("minLevel", "VIEWER")
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(true))
                .andExpect(jsonPath("$.result.accessLevel").value("VIEWER"));
    }

    @Test
    void access_unknownUser_returnsAllowedFalse() throws Exception {
        UUID recipientId = createRecipient(ownerId, "Bác Phạm");
        UUID stranger = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/care-recipients/" + recipientId + "/access")
                        .param("userId", stranger.toString())
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(false))
                .andExpect(jsonPath("$.result.accessLevel").doesNotExist());
    }

    @Test
    void access_nonExistentRecipient_returnsAllowedFalse() throws Exception {
        UUID randomRecipient = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/care-recipients/" + randomRecipient + "/access")
                        .param("userId", ownerId.toString())
                        .header("Authorization", serviceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.allowed").value(false));
    }

    // ===== 3. POST /api/v1/care-recipients/batch =====

    @Test
    void batch_unauthenticated_returns401() throws Exception {
        RecipientBatchRequest req = RecipientBatchRequest.builder()
                .recipientIds(List.of(UUID.randomUUID()))
                .build();
        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void batch_emptyRecipientIds_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_IDS_REQUIRED.getCode()));
    }

    @Test
    void batch_tooManyRecipientIds_returns400() throws Exception {
        List<UUID> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(UUID.randomUUID());
        }
        RecipientBatchRequest req = RecipientBatchRequest.builder().recipientIds(tooMany).build();

        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.RECIPIENT_BATCH_TOO_LARGE.getCode()));
    }

    @Test
    void batch_duplicateIds_dedupesAndReturnsSingleInstance() throws Exception {
        UUID id1 = createRecipient(ownerId, "Bệnh Nhân 1");
        RecipientBatchRequest req = RecipientBatchRequest.builder()
                .recipientIds(List.of(id1, id1, id1))
                .build();

        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(id1.toString()));
    }

    @Test
    void batch_softDeletedRecipient_isExcludedFromResults() throws Exception {
        UUID id1 = createRecipient(ownerId, "Sống");
        UUID id2 = createRecipient(ownerId, "Bị Xóa");
        mockMvc.perform(delete("/api/v1/care-recipients/" + id2)
                        .header("Authorization", callerToken))
                .andExpect(status().isOk());

        RecipientBatchRequest req = RecipientBatchRequest.builder()
                .recipientIds(List.of(id1, id2))
                .build();

        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(id1.toString()));
    }

    @Test
    void batch_skipsNonExistentIds() throws Exception {
        UUID id1 = createRecipient(ownerId, "Có Thật");
        UUID fake1 = UUID.randomUUID();
        UUID fake2 = UUID.randomUUID();

        RecipientBatchRequest req = RecipientBatchRequest.builder()
                .recipientIds(List.of(fake1, id1, fake2))
                .build();

        mockMvc.perform(post("/api/v1/care-recipients/batch")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].id").value(id1.toString()));
    }

    // ===== Helpers =====

    private UUID createRecipient(UUID userId, String fullName) throws Exception {
        String body = """
                {"fullName":"%s","dateOfBirth":"1950-01-01","gender":"MALE","bloodType":"A+",
                 "addressLine":"100 Hai Bà Trưng","district":"Quận 1","province":"TP.HCM","latitude":10.78,"longitude":106.70,
                 "allergies":"Hải sản","medicalHistory":"Cao huyết áp","mobilityLevel":"INDEPENDENT",
                 "relationshipType":"CHILD",
                 "emergencyContacts":[{"fullName":"Người Liên Hệ","relationship":"Con","phoneNumber":"0912345678","primary":true}],
                 "medications":[{"medicationName":"Amlodipine","route":"ORAL","startDate":"2026-01-01","timeOfDay":["08:00"]}]}
                """.formatted(fullName);
        MvcResult res = mockMvc.perform(post("/api/v1/care-recipients")
                        .header("Authorization", "Bearer " + IDENTITY.accessToken(userId, "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString()).get("result");
        return UUID.fromString(node.get("profile").get("id").asString());
    }

    private UUID addManager(UUID recipientId, String accessLevel) throws Exception {
        String email = "manager-" + UUID.randomUUID() + "@example.test";
        UUID userId = IDENTITY.addUser(email, "Manager", "ACTIVE", Set.of("CUSTOMER"));
        String body = """
                {"email":"%s","relationshipType":"CHILD","accessLevel":"%s"}
                """.formatted(email, accessLevel);
        // C-04: người thân chỉ trở thành người quản lý khi tự chấp nhận lời mời.
        MvcResult invitation = mockMvc.perform(post("/api/v1/care-recipients/" + recipientId + "/invitations")
                        .header("Authorization", callerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String invitationId = objectMapper.readTree(invitation.getResponse().getContentAsString())
                .get("result").get("id").asString();
        mockMvc.perform(post("/api/v1/care-recipients/invitations/" + invitationId + "/accept")
                        .header("Authorization", "Bearer " + IDENTITY.accessToken(userId, "CUSTOMER")))
                .andExpect(status().isOk());
        return userId;
    }
}
