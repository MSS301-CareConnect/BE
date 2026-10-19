package mss301.apigateway;

import jakarta.servlet.http.Cookie;
import mss301.apigateway.support.TestUpstreams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gateway thật (MockMvc) chuyển tiếp tới IdentityService / CareRecipientService giả qua HTTP thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGatewayTests {

    static final String FRONTEND_ORIGIN = "https://web.test";
    static final TestUpstreams UPSTREAMS = TestUpstreams.get();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.identity-url", UPSTREAMS::identityUrl);
        registry.add("gateway.care-recipient-url", UPSTREAMS::careRecipientUrl);
        registry.add("gateway.booking-url", UPSTREAMS::bookingUrl);
        registry.add("gateway.provider-url", UPSTREAMS::providerUrl);
        registry.add("gateway.care-catalog-url", UPSTREAMS::careCatalogUrl);
        registry.add("gateway.media-url", UPSTREAMS::mediaUrl);
        registry.add("gateway.allowed-origins", () -> FRONTEND_ORIGIN);
        registry.add("gateway.jwt.issuer", () -> TestUpstreams.ISSUER);
        registry.add("gateway.jwt.jwks-uri", UPSTREAMS::jwksUri);
        registry.add("spring.http.clients.read-timeout", () -> "1s");
    }

    @Autowired
    MockMvc mockMvc;

    // ===== Định tuyến giữ nguyên /api/v1 =====

    @Test
    void routesForwardApiPathToEachService() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.service").value("identity"))
                .andExpect(jsonPath("$.result.path").value("/api/v1/users/me"));
        mockMvc.perform(get("/api/v1/care-recipients").param("keyword", "bố").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.result.service").value("care-recipient"))
                .andExpect(jsonPath("$.result.path").value("/api/v1/care-recipients"))
                .andExpect(jsonPath("$.result.query").exists());
        for (String path : List.of("/bookings", "/bookings/" + id + "/occurrences", "/booking-occurrences/" + id)) {
            mockMvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.service").value("booking"))
                    .andExpect(jsonPath("$.result.path").value("/api/v1" + path));
        }
        for (String path : List.of("/care-centers", "/caregivers/me", "/work-shifts/" + id, "/certificates/caregivers/" + id,
                "/provider-media/CAREGIVER/" + id)) {
            mockMvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.result.service").value("provider"))
                    .andExpect(jsonPath("$.result.path").value("/api/v1" + path));
        }
        for (String path : List.of("/catalog-items", "/center-services/center/" + id, "/price-plans/" + id)) {
            mockMvc.perform(get("/api/v1" + path).header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.result.service").value("care-catalog"))
                    .andExpect(jsonPath("$.result.path").value("/api/v1" + path));
        }
        mockMvc.perform(get("/api/v1/media/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.result.service").value("media"))
                .andExpect(jsonPath("$.result.path").value("/api/v1/media/" + id));
        mockMvc.perform(post("/api/v1/bookings/" + id + "/cancel").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "k-1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.result.service").value("booking"))
                .andExpect(jsonPath("$.result.headers['idempotency-key']").value("k-1"));
    }

    @Test
    void unknownPathIs404Envelope() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        mockMvc.perform(get("/api/v1/unknown-resources").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    // ===== Xác thực tại gateway =====

    @Test
    void protectedRouteWithoutTokenIs401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/care-recipients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void tokensWithoutApiAudienceOrOfRefreshTypeAreRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        String identityOnly = UPSTREAMS.token(userId, List.of("careconnect-identity"), "access", List.of("CUSTOMER"));
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + identityOnly))
                .andExpect(status().isUnauthorized());
        String refresh = UPSTREAMS.token(userId, List.of("careconnect-identity", "careconnect-api"), "refresh", List.of());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void publicTokenFlowIgnoresStaleCookie() throws Exception {
        // Cookie access_token hỏng/hết hạn không được chặn login — gateway không đọc token ở endpoint public.
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(new Cookie("access_token", "expired.or.broken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\",\"password\":\"x\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.path").value("/api/v1/auth/register"));
    }

    // ===== Cookie web đi qua gateway (Identity đặt Path=/ nên không cần rewrite) =====

    @Test
    void identityCookiesPassThroughWithPathRoot() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andReturn();
        List<String> setCookies = login.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).hasSize(3);
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("access_token=new-access") && cookie.contains("Path=/;"));
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("refresh_token=new-refresh") && cookie.contains("Path=/;"));
        assertThat(setCookies).anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN=new-csrf") && cookie.contains("Path=/;"));
        // Cookie Path=/ được trình duyệt gửi cho mọi /api/v1/** → gateway xác thực bằng cookie và chuyển nguyên
        // cookie + header CSRF xuống service.
        String accessToken = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        mockMvc.perform(post("/api/v1/care-recipients")
                        .cookie(new Cookie("access_token", accessToken), new Cookie("refresh_token", "r"),
                                new Cookie("XSRF-TOKEN", "csrf-1"))
                        .header("X-XSRF-TOKEN", "csrf-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.headers.cookie").value(org.hamcrest.Matchers.containsString("access_token=" + accessToken)))
                .andExpect(jsonPath("$.result.headers.cookie").value(org.hamcrest.Matchers.containsString("refresh_token=r")))
                .andExpect(jsonPath("$.result.headers.cookie").value(org.hamcrest.Matchers.containsString("XSRF-TOKEN=csrf-1")))
                .andExpect(jsonPath("$.result.headers['x-xsrf-token']").value("csrf-1"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "r"), new Cookie("XSRF-TOKEN", "csrf-1"))
                        .header("X-XSRF-TOKEN", "csrf-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.path").value("/api/v1/auth/refresh"))
                .andExpect(jsonPath("$.result.headers.cookie").value(org.hamcrest.Matchers.containsString("refresh_token=r")));
    }

    // ===== Header do gateway quản lý =====

    @Test
    void clientUserHeadersAreReplacedFromToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = UPSTREAMS.accessToken(userId, "CUSTOMER", "ADMIN");
        mockMvc.perform(get("/api/v1/care-recipients")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "forged")
                        .header("X-User-Email", "forged@example.test")
                        .header("x-user-roles", "ADMIN")
                        .header("X-Internal-Secret", "guess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.headers['x-user-id']").value(userId.toString()))
                .andExpect(jsonPath("$.result.headers['x-user-roles']").value("CUSTOMER,ADMIN"))
                .andExpect(jsonPath("$.result.headers['x-user-email']").doesNotExist())
                .andExpect(jsonPath("$.result.headers['x-internal-secret']").doesNotExist())
                .andExpect(jsonPath("$.result.headers.authorization").value("Bearer " + token));
        // Endpoint public: không có user → không có X-User-*, kể cả header client tự gửi.
        mockMvc.perform(post("/api/v1/auth/register").header("X-User-Id", "forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.result.headers['x-user-id']").doesNotExist());
    }

    @Test
    void requestIdIsKeptWhenValidAndForwardedOnce() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        MvcResult result = mockMvc.perform(get("/api/v1/care-recipients")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "client-request-0001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.headers['x-request-id']").value("client-request-0001"))
                .andReturn();
        assertThat(result.getResponse().getHeaders("X-Request-Id")).containsExactly("client-request-0001");
        mockMvc.perform(get("/api/v1/care-recipients")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "bad id\r\ninjected"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    // ===== Không còn /internal =====

    @Test
    void internalPathsAreNotRoutedToAnyService() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "ADMIN");
        int before = UPSTREAMS.receivedPaths().size();
        for (String path : List.of("/internal/users/batch", "/api/v1/internal/users/x")) {
            mockMvc.perform(get(URI.create(path)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
        assertThat(UPSTREAMS.receivedPaths().size()).isEqualTo(before);
    }

    // ===== CORS tập trung =====

    @Test
    void corsHandledAtGatewayAndOriginNotForwarded() throws Exception {
        mockMvc.perform(options("/api/v1/care-recipients")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        MvcResult result = mockMvc.perform(get("/api/v1/care-recipients")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.headers.origin").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getHeaders("Access-Control-Allow-Origin")).containsExactly(FRONTEND_ORIGIN);
        mockMvc.perform(options("/api/v1/care-recipients")
                        .header("Origin", "https://evil.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    // ===== Swagger tổng hợp + timeout =====

    @Test
    void aggregatedApiDocsArePublic() throws Exception {
        mockMvc.perform(get("/api-docs/identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CareConnect IdentityService"));
    }

    @Test
    void slowUpstreamIs504Envelope() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        mockMvc.perform(get("/api/v1/users/slow").header("Authorization", "Bearer " + token))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(9003));
    }
}