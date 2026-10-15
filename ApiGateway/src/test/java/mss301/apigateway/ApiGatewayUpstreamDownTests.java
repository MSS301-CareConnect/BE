package mss301.apigateway;

import mss301.apigateway.support.TestUpstreams;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Service phía sau không chạy (cổng đóng) / JWKS không lấy được → 503 đúng envelope, không 500, không giả 401.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiGatewayUpstreamDownTests {

    static final TestUpstreams UPSTREAMS = TestUpstreams.get();
    static final String CLOSED_URL = "http://127.0.0.1:" + findClosedPort();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.identity-url", () -> CLOSED_URL);
        registry.add("gateway.care-recipient-url", () -> CLOSED_URL);
        registry.add("gateway.booking-url", () -> CLOSED_URL);
        registry.add("gateway.allowed-origins", () -> "https://web.test");
        registry.add("gateway.jwt.issuer", () -> TestUpstreams.ISSUER);
        registry.add("gateway.jwt.jwks-uri", () -> CLOSED_URL + "/.well-known/jwks.json");
    }

    static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void downUpstreamIs503Envelope() throws Exception {
        // Endpoint public (không cần JWKS) tới service đang tắt.
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(9001))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void unreachableJwksIs503NotUnauthenticated() throws Exception {
        String token = UPSTREAMS.accessToken(UUID.randomUUID(), "CUSTOMER");
        mockMvc.perform(get("/api/v1/care-recipients").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(9002));
    }
}
