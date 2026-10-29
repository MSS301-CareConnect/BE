package mss301.paymentservice.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Các service ngoài giả cho test PaymentService trên một HttpServer: Identity (JWKS + token SERVICE), BookingService
 * (payment-context, deposit-paid) và API merchant VNPay (refund) có thể chỉnh mã trả / độ trễ / HTTP status.
 */
public final class PaymentTestServer {

    public static final String ISSUER = "https://identity.test";
    public static final String KEY_ID = "payment-test-key";
    public static final String VNPAY_SECRET = "vnpay-test-secret";
    public static final String VNPAY_TMN_CODE = "TESTTMN";
    public static final String VNPAY_API_PATH = "/vnpay/api";

    private static final PaymentTestServer INSTANCE = new PaymentTestServer();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> bookings = new ConcurrentHashMap<>();
    private final List<UUID> depositPaidCalls = new CopyOnWriteArrayList<>();
    private final AtomicInteger depositPaidStatus = new AtomicInteger(200);
    private final AtomicReference<String> vnPayResponseCode = new AtomicReference<>("00");
    private final AtomicInteger vnPayHttpStatus = new AtomicInteger(200);
    private final AtomicLong vnPayDelayMillis = new AtomicLong(0);
    private final AtomicReference<String> queryTransactionStatus = new AtomicReference<>("00");
    private final List<JsonNode> vnPayRequests = new CopyOnWriteArrayList<>();
    private final RSAKey signingKey;
    private final HttpServer server;

    private PaymentTestServer() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
        server.createContext("/.well-known/jwks.json", this::handleJwks);
        server.createContext("/api/v1/auth/service-token", this::handleServiceToken);
        server.createContext("/api/v1/bookings/", this::handleBookings);
        server.createContext(VNPAY_API_PATH, this::handleVnPayApi);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public static PaymentTestServer get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String jwksUri() {
        return baseUrl() + "/.well-known/jwks.json";
    }

    public void reset() {
        depositPaidCalls.clear();
        depositPaidStatus.set(200);
        vnPayResponseCode.set("00");
        vnPayHttpStatus.set(200);
        vnPayDelayMillis.set(0);
        vnPayRequests.clear();
        queryTransactionStatus.set("00");
    }

    // querydr giả: vnp_TransactionStatus trả về ("00" đã thu, "01" chưa hoàn tất).
    public void setQueryTransactionStatus(String transactionStatus) {
        queryTransactionStatus.set(transactionStatus);
    }

    // ===== dữ liệu giả =====

    public void putBooking(UUID bookingId, UUID customerId, UUID centerId, String status, Instant depositDueAt,
                           List<Map<String, Object>> occurrences) {
        Map<String, Object> booking = new LinkedHashMap<>();
        booking.put("bookingId", bookingId.toString());
        booking.put("bookingCode", "BK-" + bookingId.toString().substring(0, 8));
        booking.put("customerId", customerId.toString());
        booking.put("centerId", centerId.toString());
        booking.put("status", status);
        booking.put("totalAmount", 0);
        if (depositDueAt != null) {
            booking.put("depositDueAt", depositDueAt.toString());
        }
        booking.put("occurrences", occurrences);
        bookings.put(bookingId, booking);
    }

    public static Map<String, Object> occurrence(UUID occurrenceId, int occurrenceNo, Instant startAt, String status,
                                                 Long priceAmount) {
        Map<String, Object> occurrence = new LinkedHashMap<>();
        occurrence.put("occurrenceId", occurrenceId.toString());
        occurrence.put("occurrenceNo", occurrenceNo);
        occurrence.put("startAt", startAt.toString());
        occurrence.put("endAt", startAt.plusSeconds(3600).toString());
        occurrence.put("status", status);
        if (priceAmount != null) {
            occurrence.put("priceAmount", priceAmount);
        }
        occurrence.put("hasSession", false);
        return occurrence;
    }

    public List<UUID> depositPaidCalls() {
        return depositPaidCalls;
    }

    public void setDepositPaidStatus(int status) {
        depositPaidStatus.set(status);
    }

    public void setVnPayResponse(String responseCode, int httpStatus, long delayMillis) {
        vnPayResponseCode.set(responseCode);
        vnPayHttpStatus.set(httpStatus);
        vnPayDelayMillis.set(delayMillis);
    }

    public List<JsonNode> vnPayRequests() {
        return vnPayRequests;
    }

    public String bearer(UUID userId, UUID centerId, String... roles) {
        return "Bearer " + accessToken(userId, centerId, roles);
    }

    public String accessToken(UUID userId, UUID centerId, String... roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of("careconnect-api"))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .claim("roles", List.of(roles))
                .claim("permissions", List.of());
        if (centerId != null) {
            claims.claim("center_id", centerId.toString());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims.build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    // ===== handlers =====

    private void handleJwks(HttpExchange exchange) throws IOException {
        write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
    }

    private void handleServiceToken(HttpExchange exchange) throws IOException {
        Map<String, Object> token = new LinkedHashMap<>();
        token.put("accessToken", accessToken(UUID.randomUUID(), null, "SERVICE"));
        token.put("tokenType", "Bearer");
        token.put("expiresInSeconds", 600);
        writeSuccess(exchange, token);
    }

    private void handleBookings(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            write(exchange, 401, "{\"code\":401}");
            return;
        }
        String[] parts = exchange.getRequestURI().getPath().split("/");
        UUID bookingId = UUID.fromString(parts[4]);
        String action = parts[5];
        if ("payment-context".equals(action)) {
            Map<String, Object> booking = bookings.get(bookingId);
            if (booking == null) {
                write(exchange, 404, "{\"code\":5001,\"message\":\"not found\"}");
                return;
            }
            writeSuccess(exchange, booking);
            return;
        }
        if ("deposit-paid".equals(action)) {
            exchange.getRequestBody().readAllBytes();
            int status = depositPaidStatus.get();
            if (status != 200) {
                write(exchange, status, "{\"code\":5999,\"message\":\"fail\"}");
                return;
            }
            depositPaidCalls.add(bookingId);
            writeSuccess(exchange, Map.of("id", bookingId.toString()));
            return;
        }
        write(exchange, 404, "{\"code\":404}");
    }

    private void handleVnPayApi(HttpExchange exchange) throws IOException {
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        vnPayRequests.add(body);
        long delay = vnPayDelayMillis.get();
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", UUID.randomUUID().toString().replace("-", ""));
        String command = body.get("vnp_Command").asString();
        response.put("vnp_Command", command);
        if ("querydr".equals(command)) {
            response.put("vnp_TxnRef", body.get("vnp_TxnRef").asString());
            response.put("vnp_TransactionNo", "99" + Math.abs(body.get("vnp_TxnRef").asString().hashCode()));
            response.put("vnp_TransactionStatus", queryTransactionStatus.get());
        }
        response.put("vnp_ResponseCode", vnPayResponseCode.get());
        response.put("vnp_Message", "test");
        try {
            write(exchange, vnPayHttpStatus.get(), objectMapper.writeValueAsString(response));
        } catch (IOException e) {
            // Client đã bỏ đi vì timeout.
        }
    }

    private void writeSuccess(HttpExchange exchange, Object result) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        envelope.put("message", "OK");
        envelope.put("result", result);
        write(exchange, 200, objectMapper.writeValueAsString(envelope));
    }

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }
}
