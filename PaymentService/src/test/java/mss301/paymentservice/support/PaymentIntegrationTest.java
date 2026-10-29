package mss301.paymentservice.support;

import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.response.RefundTier;
import mss301.paymentservice.entity.PlatformPolicy;
import mss301.paymentservice.enums.PolicyScope;
import mss301.paymentservice.enums.RefundMode;
import mss301.paymentservice.gateway.VnPaySigner;
import mss301.paymentservice.repository.PlatformPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nền chung cho test tích hợp PaymentService: SpringBootTest + MockMvc, H2 create-drop, JWT thật (RS256, JWKS từ
 * {@link PaymentTestServer}), Feign thật tới Booking giả, VNPay giả. Scheduler không tự chạy — test gọi service trực tiếp.
 * Mọi lớp con dùng chung MỘT Spring context (cùng cấu hình).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentIntegrationTest.TestClockConfig.class)
public abstract class PaymentIntegrationTest {

    protected static final PaymentTestServer SERVER = PaymentTestServer.get();
    protected static final Instant BASE = Instant.parse("2026-11-02T01:00:00Z");
    protected static final UUID GLOBAL_POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    // -Dpayment.testing.postgres=true: chạy trên PostgreSQL thật với schema/payment.sql + ddl-auto validate (kiểm SQL tay
    // khớp entity). Container test riêng, không trỏ vào DB dev.
    static final boolean POSTGRES = Boolean.getBoolean("payment.testing.postgres");
    static final String PG_URL = System.getProperty("payment.testing.postgres-url",
            "jdbc:postgresql://localhost:55497/postgres");
    static final String PG_USER = "postgres";
    static final String PG_PASSWORD = "payment_test_only";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String schema = "payment_test_" + UUID.randomUUID().toString().replace("-", "");
        if (POSTGRES) {
            createSchema(schema);
        }
        registry.add("spring.datasource.url", () -> POSTGRES
                ? PG_URL + "?currentSchema=" + schema
                : "jdbc:h2:mem:payment;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> POSTGRES ? PG_USER : "sa");
        registry.add("spring.datasource.password", () -> POSTGRES ? PG_PASSWORD : "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> POSTGRES ? "validate" : "create-drop");
        registry.add("spring.sql.init.mode", () -> POSTGRES ? "always" : "never");
        registry.add("spring.sql.init.schema-locations", () -> "file:./schema/payment.sql");
        registry.add("payment.jwt.issuer", () -> PaymentTestServer.ISSUER);
        registry.add("payment.jwt.jwks-uri", SERVER::jwksUri);
        registry.add("payment.booking-url", SERVER::baseUrl);
        registry.add("payment.identity-url", SERVER::baseUrl);
        registry.add("payment.service-client.client-id", () -> "payment");
        registry.add("payment.service-client.client-secret", () -> "payment-test-secret");
        registry.add("payment.vnpay.enabled", () -> "true");
        registry.add("payment.vnpay.tmn-code", () -> PaymentTestServer.VNPAY_TMN_CODE);
        registry.add("payment.vnpay.hash-secret", () -> PaymentTestServer.VNPAY_SECRET);
        registry.add("payment.vnpay.api-url", () -> SERVER.baseUrl() + PaymentTestServer.VNPAY_API_PATH);
        registry.add("payment.vnpay.read-timeout", () -> "1s");
        registry.add("payment.refund.mode", () -> "SIMULATED");
        registry.add("payment.refund.scan-interval", () -> "1h");
        registry.add("payment.invoice.overdue-scan-interval", () -> "1h");
        registry.add("payment.outbox.scan-interval", () -> "1h");
        registry.add("payment.expiry.scan-interval", () -> "1h");
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
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected PaymentProperties paymentProperties;

    @Autowired
    protected PlatformPolicyRepository platformPolicyRepository;

    @BeforeEach
    void resetEnvironment() {
        clock.set(BASE);
        SERVER.reset();
        paymentProperties.getRefund().setMode(RefundMode.SIMULATED);
        paymentProperties.getRefund().setMaxAttempts(5);
        paymentProperties.getVnpay().setQuerydrEnabled(false);
        if (!platformPolicyRepository.existsById(GLOBAL_POLICY_ID)) {
            platformPolicyRepository.save(globalPolicy());
        }
    }

    // Giống seed trong schema/payment.sql.
    protected static PlatformPolicy globalPolicy() {
        return PlatformPolicy.builder()
                .id(GLOBAL_POLICY_ID)
                .scope(PolicyScope.GLOBAL)
                .effectiveFrom(Instant.parse("2026-01-01T00:00:00Z"))
                .depositPercent(BigDecimal.valueOf(30))
                .commissionPercent(BigDecimal.TEN)
                .paymentSessionTtlMinutes(15)
                .depositDueHours(24)
                .coolingOffMinutes(60)
                .coolingOffMinLeadHours(24)
                .refundTiers(List.of(new RefundTier(0, 24, BigDecimal.valueOf(30)),
                        new RefundTier(24, 72, BigDecimal.TEN),
                        new RefundTier(72, null, BigDecimal.ZERO)))
                .customerNoShowChargePercent(BigDecimal.valueOf(100))
                .remainingDueHours(48)
                .note("test")
                .build();
    }

    // ===== token =====

    protected String customer(UUID customerId) {
        return SERVER.bearer(customerId, null, "CUSTOMER");
    }

    protected String service() {
        return SERVER.bearer(UUID.randomUUID(), null, "SERVICE");
    }

    protected String admin() {
        return SERVER.bearer(UUID.randomUUID(), null, "ADMIN");
    }

    protected String manager(UUID centerId) {
        return SERVER.bearer(UUID.randomUUID(), centerId, "CENTER_MANAGER");
    }

    // ===== luồng cọc =====

    protected BookingSetup bookingWithPrices(UUID customerId, UUID centerId, long... prices) {
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = new ArrayList<>();
        List<Instant> startTimes = new ArrayList<>();
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            UUID occurrenceId = UUID.randomUUID();
            Instant startAt = BASE.plus(Duration.ofDays(5 + i));
            occurrenceIds.add(occurrenceId);
            startTimes.add(startAt);
            occurrences.add(PaymentTestServer.occurrence(occurrenceId, i + 1, startAt, "SCHEDULED", prices[i]));
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_DEPOSIT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        return new BookingSetup(bookingId, customerId, centerId, occurrenceIds, startTimes);
    }

    protected BookingSetup bookingWithStarts(UUID customerId, UUID centerId, long price, List<Instant> startTimes) {
        UUID bookingId = UUID.randomUUID();
        List<UUID> occurrenceIds = new ArrayList<>();
        List<Map<String, Object>> occurrences = new ArrayList<>();
        for (int i = 0; i < startTimes.size(); i++) {
            UUID occurrenceId = UUID.randomUUID();
            occurrenceIds.add(occurrenceId);
            occurrences.add(PaymentTestServer.occurrence(occurrenceId, i + 1, startTimes.get(i), "SCHEDULED", price));
        }
        SERVER.putBooking(bookingId, customerId, centerId, "AWAITING_DEPOSIT", BASE.plus(Duration.ofHours(24)),
                occurrences);
        return new BookingSetup(bookingId, customerId, centerId, occurrenceIds, startTimes);
    }

    protected JsonNode checkout(BookingSetup booking) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/payments/bookings/{id}/checkout", booking.bookingId())
                        .header("Authorization", customer(booking.customerId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"VNPAY\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("result");
    }

    // Thanh toán cọc thành công qua IPN tại thời điểm hiện tại của clock.
    protected JsonNode payDeposit(BookingSetup booking) throws Exception {
        JsonNode checkout = checkout(booking);
        ipn(checkout, "00").andExpect(status().isOk());
        return checkout;
    }

    protected ResultActions ipn(JsonNode checkout, String responseCode) throws Exception {
        Map<String, String> params = checkoutParams(checkout);
        return ipnWith(params.get("vnp_TxnRef"), params.get("vnp_Amount"), responseCode);
    }

    protected ResultActions ipnWith(String reference, String amountMinor, String responseCode) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", PaymentTestServer.VNPAY_TMN_CODE);
        params.put("vnp_TxnRef", reference);
        params.put("vnp_Amount", amountMinor);
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionStatus", responseCode);
        params.put("vnp_TransactionNo", "1400" + Math.abs(reference.hashCode()));
        params.put("vnp_PayDate", "20261102080000");
        String signed = VnPaySigner.signedQuery(params, PaymentTestServer.VNPAY_SECRET);
        params.put("vnp_SecureHash", signed.substring(signed.indexOf("vnp_SecureHash=") + "vnp_SecureHash=".length()));
        return mockMvc.perform(withParams(get("/api/v1/payments/vnpay/ipn"), params));
    }

    protected MockHttpServletRequestBuilder withParams(MockHttpServletRequestBuilder builder, Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            builder.param(entry.getKey(), entry.getValue());
        }
        return builder;
    }

    protected Map<String, String> checkoutParams(JsonNode checkout) {
        Map<String, String> params = new HashMap<>();
        UriComponentsBuilder.fromUriString(checkout.get("checkoutUrl").asString()).build().getQueryParams()
                .forEach((key, values) -> params.put(key, values.getFirst()));
        return params;
    }

    protected JsonNode body(ResultActions resultActions) throws Exception {
        return objectMapper.readTree(resultActions.andReturn().getResponse().getContentAsString());
    }

    public record BookingSetup(UUID bookingId, UUID customerId, UUID centerId, List<UUID> occurrenceIds,
                                  List<Instant> startTimes) {
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(BASE);
        }
    }
}
