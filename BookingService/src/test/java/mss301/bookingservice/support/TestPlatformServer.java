package mss301.bookingservice.support;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Các service phía ngoài giả cho test BookingService trên một HttpServer: Identity (JWKS + /internal/users/batch),
 * CareRecipient (/internal/care-recipients/{id}/access|snapshot), Provider (/internal/centers|caregivers/{id}).
 * Mỗi service kiểm secret RIÊNG của mình; có thể tắt từng service (500) để kiểm fallback / 503.
 */
public final class TestPlatformServer {

    public static final String ISSUER = "https://identity.test";
    public static final String KEY_ID = "booking-test-key";
    public static final String IDENTITY_SECRET = "identity-internal-test-secret";
    public static final String CARE_RECIPIENT_SECRET = "care-recipient-internal-test-secret";
    public static final String PROVIDER_SECRET = "provider-internal-test-secret";

    private static final TestPlatformServer INSTANCE = new TestPlatformServer();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> users = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> recipients = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> recipientManagers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> centers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> caregivers = new ConcurrentHashMap<>();
    private final AtomicBoolean identityFailing = new AtomicBoolean(false);
    private final AtomicBoolean careRecipientFailing = new AtomicBoolean(false);
    private final AtomicBoolean providerFailing = new AtomicBoolean(false);
    private final AtomicReference<String> lastCareRecipientRequestId = new AtomicReference<>();
    private final RSAKey signingKey;
    private final HttpServer server;

    private TestPlatformServer() {
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
        server.createContext("/internal/users", this::handleUsers);
        server.createContext("/internal/care-recipients", this::handleCareRecipients);
        server.createContext("/internal/centers", this::handleCenters);
        server.createContext("/internal/caregivers", this::handleCaregivers);
        server.start();
    }

    public static TestPlatformServer get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String jwksUri() {
        return baseUrl() + "/.well-known/jwks.json";
    }

    // ===== dữ liệu giả =====

    public UUID addUser(String fullName, String phoneNumber) {
        UUID id = UUID.randomUUID();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id.toString());
        user.put("email", id + "@user.test");
        user.put("fullName", fullName);
        user.put("phoneNumber", phoneNumber);
        user.put("status", "ACTIVE");
        user.put("roles", Set.of("CUSTOMER"));
        users.put(id, user);
        return id;
    }

    public UUID addRecipient(UUID ownerId, String fullName, String status) {
        UUID id = UUID.randomUUID();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("recipientId", id.toString());
        snapshot.put("fullName", fullName);
        snapshot.put("dateOfBirth", "1945-05-20");
        snapshot.put("age", 81);
        snapshot.put("gender", "FEMALE");
        snapshot.put("phoneNumber", "0901234567");
        snapshot.put("addressLine", "12 Lý Thường Kiệt");
        snapshot.put("ward", "Phường Hàng Bài");
        snapshot.put("district", "Hoàn Kiếm");
        snapshot.put("province", "Hà Nội");
        snapshot.put("latitude", 21.0245);
        snapshot.put("longitude", 105.8412);
        snapshot.put("bloodType", "O+");
        snapshot.put("mobilityLevel", "ASSISTED");
        snapshot.put("independenceLevel", "PARTIAL");
        snapshot.put("allergies", "Penicillin");
        snapshot.put("chronicConditions", "Tăng huyết áp");
        snapshot.put("status", status);
        snapshot.put("activeMedications", List.of(Map.of("id", UUID.randomUUID().toString(),
                "medicationName", "Amlodipine", "dosage", "5mg", "timeOfDay", List.of("08:00"),
                "startDate", "2026-01-01", "active", true)));
        snapshot.put("primaryEmergencyContact", Map.of("fullName", "Nguyễn Văn Con", "relationship", "Con trai",
                "phoneNumber", "0912345678", "primary", true, "priority", 0));
        snapshot.put("snapshotAt", Instant.now().toString());
        // Field lạ: Booking phải bỏ qua (CareRecipient thêm field không làm hỏng Booking).
        snapshot.put("someFutureField", "ignored");
        recipients.put(id, snapshot);
        Map<UUID, String> managers = new ConcurrentHashMap<>();
        managers.put(ownerId, "OWNER");
        recipientManagers.put(id, managers);
        return id;
    }

    public void addRecipientManager(UUID recipientId, UUID userId, String accessLevel) {
        recipientManagers.get(recipientId).put(userId, accessLevel);
    }

    public UUID addCenter(String status) {
        UUID id = UUID.randomUUID();
        Map<String, Object> center = new LinkedHashMap<>();
        center.put("centerId", id.toString());
        center.put("centerName", "Trung tâm " + id.toString().substring(0, 4));
        center.put("centerStatus", status);
        center.put("managerUserId", UUID.randomUUID().toString());
        centers.put(id, center);
        return id;
    }

    // careServiceIds null = Provider chưa quản lý kỹ năng (field vắng mặt trong JSON).
    public CaregiverIds addCaregiver(UUID centerId, String verificationStatus, String caregiverStatus,
                                     List<UUID> careServiceIds) {
        UUID caregiverId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Map<String, Object> caregiver = new LinkedHashMap<>();
        caregiver.put("caregiverId", caregiverId.toString());
        caregiver.put("userId", userId.toString());
        caregiver.put("centerId", centerId.toString());
        caregiver.put("centerStatus", "ACTIVE");
        caregiver.put("fullName", "Caregiver " + caregiverId.toString().substring(0, 4));
        caregiver.put("verificationStatus", verificationStatus);
        caregiver.put("caregiverStatus", caregiverStatus);
        if (careServiceIds != null) {
            caregiver.put("careServiceIds", careServiceIds.stream().map(UUID::toString).toList());
        }
        caregivers.put(caregiverId, caregiver);
        return new CaregiverIds(caregiverId, userId);
    }

    public CaregiverIds addActiveCaregiver(UUID centerId) {
        return addCaregiver(centerId, "VERIFIED", "ACTIVE", null);
    }

    public void setIdentityFailing(boolean value) {
        identityFailing.set(value);
    }

    public void setCareRecipientFailing(boolean value) {
        careRecipientFailing.set(value);
    }

    public void setProviderFailing(boolean value) {
        providerFailing.set(value);
    }

    public String lastCareRecipientRequestId() {
        return lastCareRecipientRequestId.get();
    }

    // ===== token =====

    public String accessToken(UUID userId, UUID centerId, String... roles) {
        return token(userId, centerId, List.of("careconnect-identity", "careconnect-api"), "access", List.of(roles));
    }

    public String token(UUID userId, UUID centerId, List<String> audience, String tokenType, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", tokenType)
                .claim("authz_version", 0)
                .claim("roles", roles)
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

    private void handleUsers(HttpExchange exchange) throws IOException {
        if (rejected(exchange, IDENTITY_SECRET, identityFailing)) {
            return;
        }
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        List<Object> found = new ArrayList<>();
        for (JsonNode id : body.get("userIds")) {
            Map<String, Object> user = users.get(UUID.fromString(id.asString()));
            if (user != null) {
                found.add(user);
            }
        }
        writeSuccess(exchange, found);
    }

    private void handleCareRecipients(HttpExchange exchange) throws IOException {
        lastCareRecipientRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
        if (rejected(exchange, CARE_RECIPIENT_SECRET, careRecipientFailing)) {
            return;
        }
        String[] segments = exchange.getRequestURI().getPath().split("/");
        UUID recipientId = UUID.fromString(segments[3]);
        String action = segments[4];
        if (action.equals("access")) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            UUID userId = UUID.fromString(query.get("userId"));
            String level = recipientManagers.getOrDefault(recipientId, Map.of()).get(userId);
            boolean allowed = level != null && rank(level) >= rank(query.get("minLevel"));
            Map<String, Object> result = new HashMap<>();
            result.put("recipientId", recipientId.toString());
            result.put("userId", userId.toString());
            result.put("allowed", allowed);
            result.put("accessLevel", level);
            writeSuccess(exchange, result);
            return;
        }
        Map<String, Object> snapshot = recipients.get(recipientId);
        if (snapshot == null) {
            write(exchange, 404, "{\"code\":2100,\"message\":\"Hồ sơ người bệnh không tồn tại\"}");
            return;
        }
        writeSuccess(exchange, snapshot);
    }

    private void handleCenters(HttpExchange exchange) throws IOException {
        if (rejected(exchange, PROVIDER_SECRET, providerFailing)) {
            return;
        }
        UUID centerId = UUID.fromString(exchange.getRequestURI().getPath().substring("/internal/centers/".length()));
        writeOrNotFound(exchange, centers.get(centerId), "{\"code\":4100,\"message\":\"Trung tâm chăm sóc không tồn tại\"}");
    }

    private void handleCaregivers(HttpExchange exchange) throws IOException {
        if (rejected(exchange, PROVIDER_SECRET, providerFailing)) {
            return;
        }
        UUID caregiverId = UUID.fromString(exchange.getRequestURI().getPath().substring("/internal/caregivers/".length()));
        writeOrNotFound(exchange, caregivers.get(caregiverId), "{\"code\":4200,\"message\":\"Hồ sơ người chăm sóc không tồn tại\"}");
    }

    private boolean rejected(HttpExchange exchange, String expectedSecret, AtomicBoolean failing) throws IOException {
        if (!expectedSecret.equals(exchange.getRequestHeaders().getFirst("X-Internal-Secret"))) {
            write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
            return true;
        }
        if (failing.get()) {
            write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
            return true;
        }
        return false;
    }

    private static int rank(String level) {
        return switch (level) {
            case "OWNER" -> 3;
            case "EDITOR" -> 2;
            default -> 1;
        };
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(parts[0], parts.length > 1 ? parts[1] : "");
        }
        return query;
    }

    private void writeOrNotFound(HttpExchange exchange, Map<String, Object> result, String notFoundBody) throws IOException {
        if (result == null) {
            write(exchange, 404, notFoundBody);
            return;
        }
        writeSuccess(exchange, result);
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
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    public record CaregiverIds(UUID caregiverId, UUID userId) {
    }
}
