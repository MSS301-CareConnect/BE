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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.ParseException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Các service phía ngoài giả cho test BookingService trên một HttpServer: Identity (JWKS + /internal/users/batch),
 * CareRecipient (/internal/care-recipients/{id}/access|snapshot), Provider (/internal/centers|caregivers/{id}).
 * Mỗi service kiểm secret RIÊNG của mình; có thể tắt từng service (500) để kiểm fallback / 503.
 */
public final class TestPlatformServer {

    public static final String ISSUER = "https://identity.test";
    public static final UUID DEFAULT_CARE_SERVICE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
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
    private final AtomicBoolean catalogFailing = new AtomicBoolean(false);
    private final AtomicBoolean paymentFailing = new AtomicBoolean(false);
    // Gói giá Catalog giả theo pricePlanId; gói chưa khai báo = HOUR 60 phút 100.000đ.
    private final Map<UUID, Map<String, Object>> pricePlans = new ConcurrentHashMap<>();
    // Idempotency-Key của từng lần PaymentService giả nhận yêu cầu tạo hóa đơn REMAINING (kể cả lần trùng).
    private final List<String> remainingInvoiceKeys = new CopyOnWriteArrayList<>();
    private final List<String> refundKeys = new CopyOnWriteArrayList<>();
    // NULL = bảng mốc mặc định; có giá trị = mọi buổi hoàn đúng % này (giả lập chính sách / mốc đã đổi).
    private final AtomicReference<Integer> refundPercentOverride = new AtomicReference<>();
    // Khách đang nợ (Payment giả) và caregiver không có lịch rảnh (Provider giả) — BK-02.
    private final Set<UUID> debtors = ConcurrentHashMap.newKeySet();
    private final Set<UUID> unavailableCaregivers = ConcurrentHashMap.newKeySet();
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
        server.createContext("/api/v1/users", this::handleUsers);
        server.createContext("/api/v1/care-recipients", this::handleCareRecipients);
        server.createContext("/api/v1/care-centers", this::handleCenters);
        server.createContext("/api/v1/caregivers", this::handleCaregivers);
        server.createContext("/api/v1/center-services/quote", this::handleCatalogQuote);
        server.createContext("/api/v1/payment-policies/effective", this::handlePaymentPolicy);
        server.createContext("/api/v1/remaining-invoices", this::handleRemainingInvoice);
        server.createContext("/api/v1/refunds", this::handleRefund);
        server.createContext("/api/v1/payments", this::handlePayments);
        server.createContext("/api/v1/refund-quotes", this::handleRefundQuote);
        server.createContext("/api/v1/payments/me/outstanding", this::handleOutstanding);
        server.createContext("/api/v1/caregivers/availability-checks", this::handleAvailabilityCheck);
        server.createContext("/api/v1/auth/service-token", this::handleServiceToken);
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

    // Caregiver đủ điều kiện, làm được dịch vụ mặc định của mọi scenario test.
    public CaregiverIds addActiveCaregiver(UUID centerId) {
        return addCaregiver(centerId, "VERIFIED", "ACTIVE", List.of(DEFAULT_CARE_SERVICE_ID));
    }

    public void setIdentityFailing(boolean value) {
        identityFailing.set(value);
    }

    public void setCareRecipientFailing(boolean value) {
        careRecipientFailing.set(value);
    }

    public void setCatalogFailing(boolean value) {
        catalogFailing.set(value);
    }

    public void setPaymentFailing(boolean value) {
        paymentFailing.set(value);
    }

    public void addDebtor(UUID customerId) {
        debtors.add(customerId);
    }

    public void setCaregiverUnavailable(UUID caregiverId) {
        unavailableCaregivers.add(caregiverId);
    }

    public List<String> remainingInvoiceKeys() {
        return remainingInvoiceKeys;
    }

    public List<String> refundKeys() {
        return refundKeys;
    }

    public void setRefundPercentOverride(Integer percent) {
        refundPercentOverride.set(percent);
    }

    public void addPricePlan(UUID pricePlanId, String pricingUnit, int durationMinutes, long basePrice) {
        Map<String, Object> plan = new HashMap<>();
        plan.put("pricingUnit", pricingUnit);
        plan.put("durationMinutes", durationMinutes);
        plan.put("basePrice", basePrice);
        pricePlans.put(pricePlanId, plan);
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
        if (rejected(exchange, identityFailing)) {
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
        if (rejected(exchange, careRecipientFailing)) {
            return;
        }
        String[] segments = exchange.getRequestURI().getPath().split("/");
        UUID recipientId = UUID.fromString(segments[4]);
        String action = segments[5];
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
        if (rejected(exchange, providerFailing)) {
            return;
        }
        UUID centerId = UUID.fromString(exchange.getRequestURI().getPath().substring("/api/v1/care-centers/".length()));
        writeOrNotFound(exchange, centers.get(centerId), "{\"code\":4100,\"message\":\"Trung tâm chăm sóc không tồn tại\"}");
    }

    private void handleCaregivers(HttpExchange exchange) throws IOException {
        if (rejected(exchange, providerFailing)) {
            return;
        }
        if (exchange.getRequestURI().getPath().endsWith("/rating")) {
            objectMapper.readTree(exchange.getRequestBody());
            writeSuccess(exchange, Map.of());
            return;
        }
        UUID caregiverId = UUID.fromString(exchange.getRequestURI().getPath().substring("/api/v1/caregivers/".length()));
        writeOrNotFound(exchange, caregivers.get(caregiverId), "{\"code\":4200,\"message\":\"Hồ sơ người chăm sóc không tồn tại\"}");
    }

    // CareCatalogService giả: báo giá đúng gói khách chọn (pricePlanId bắt buộc như Catalog thật).
    private void handleCatalogQuote(HttpExchange exchange) throws IOException {
        if (rejected(exchange, catalogFailing)) {
            return;
        }
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        List<Object> quotes = new ArrayList<>();
        for (JsonNode item : body.get("items")) {
            UUID pricePlanId = UUID.fromString(item.get("pricePlanId").asString());
            Map<String, Object> plan = pricePlans.getOrDefault(pricePlanId,
                    Map.of("pricingUnit", "HOUR", "durationMinutes", 60, "basePrice", 100000));
            Map<String, Object> quote = new HashMap<>(plan);
            quote.put("careServiceId", item.get("careServiceId").asString());
            JsonNode centerServiceId = item.get("centerServiceId");
            quote.put("centerServiceId", centerServiceId == null || centerServiceId.isNull()
                    ? UUID.randomUUID().toString() : centerServiceId.asString());
            quote.put("pricePlanId", pricePlanId.toString());
            quote.put("serviceName", "Dịch vụ " + pricePlanId.toString().substring(0, 4));
            quote.put("active", true);
            quotes.add(quote);
        }
        writeSuccess(exchange, quotes);
    }

    // PaymentService giả: chính sách hiệu lực 30% cọc (C2.1).
    private void handlePaymentPolicy(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        Map<String, Object> policy = new HashMap<>();
        policy.put("policyId", "00000000-0000-0000-0000-000000000030");
        policy.put("depositPercent", 30);
        policy.put("commissionPercent", 10);
        writeSuccess(exchange, policy);
    }

    // PaymentService giả (C2.6): đọc sub của token khách để biết có nợ không.
    private volatile long reservedRefundAmount;

    private void handlePayments(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        String bookingId = parseQuery(exchange.getRequestURI().getRawQuery()).get("bookingId");
        writeSuccess(exchange, Map.of("items", List.of(Map.of("id", UUID.randomUUID().toString(), "bookingId", bookingId,
                "status", "SUCCESS", "amount", 1000000, "refundedAmount", 0,
                "reservedRefundAmount", reservedRefundAmount)), "totalPages", 1));
    }

    // M-09: tiền hoàn Payment đang giữ chỗ trên payment giả.
    public void setReservedRefundAmount(long amount) {
        reservedRefundAmount = amount;
    }

    private void handleOutstanding(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        String token = exchange.getRequestHeaders().getFirst("Authorization").substring("Bearer ".length());
        UUID customerId;
        try {
            customerId = UUID.fromString(SignedJWT.parse(token).getJWTClaimsSet().getSubject());
        } catch (ParseException e) {
            write(exchange, 401, "{\"code\":401}");
            return;
        }
        writeSuccess(exchange, Map.of("hasDebt", debtors.contains(customerId)));
    }

    // ProviderService giả (C5.2): caregiver đánh dấu không rảnh → available=false với lý do NO_SCHEDULE.
    private void handleAvailabilityCheck(HttpExchange exchange) throws IOException {
        if (rejected(exchange, providerFailing)) {
            return;
        }
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        UUID caregiverId = UUID.fromString(body.get("caregiverId").asString());
        boolean available = !unavailableCaregivers.contains(caregiverId);
        List<Object> violations = new ArrayList<>();
        if (!available) {
            JsonNode slot = body.get("slots").get(0);
            violations.add(Map.of("startAt", slot.get("startAt").asString(), "endAt", slot.get("endAt").asString(),
                    "reason", "NO_SCHEDULE"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("available", available);
        result.put("violations", violations);
        writeSuccess(exchange, result);
    }

    // IdentityService giả: cấp token SERVICE cho client-credentials của BookingService (IAM-01).
    private void handleServiceToken(HttpExchange exchange) throws IOException {
        Map<String, Object> token = new HashMap<>();
        token.put("accessToken", accessToken(UUID.randomUUID(), null, "SERVICE"));
        token.put("tokenType", "Bearer");
        token.put("expiresInSeconds", 600);
        writeSuccess(exchange, token);
    }

    // PaymentService giả: chỉ nhận token SERVICE; ghi lại Idempotency-Key để test đếm số lần gửi (OPS-04).
    private void handleRemainingInvoice(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        remainingInvoiceKeys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
        objectMapper.readTree(exchange.getRequestBody());
        writeSuccess(exchange, Map.of("amount", 0, "status", "NOT_REQUIRED"));
    }

    // C2.3: hoàn tiền (OPS-06 / BK-03 / BK-04) — ghi Idempotency-Key để test đếm số lần gửi.
    private void handleRefund(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        if (!exchange.getRequestURI().getPath().endsWith("/complaints") || !refundKeys.contains(key)) {
            refundKeys.add(key);
        }
        objectMapper.readTree(exchange.getRequestBody());
        writeSuccess(exchange, Map.of("items", List.of(), "totalRefund", 0));
    }

    // C2.2: mỗi buổi đã trả 100.000; ≥ 72h trước giờ bắt đầu hoàn 100%, 24–72h 50%, < 24h 0%.
    private void handleRefundQuote(HttpExchange exchange) throws IOException {
        if (rejected(exchange, paymentFailing)) {
            return;
        }
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        Instant cancelledAt = Instant.parse(body.get("cancelledAt").asString());
        List<Map<String, Object>> items = new ArrayList<>();
        long totalRefund = 0;
        long totalFee = 0;
        for (JsonNode occurrence : body.get("occurrences")) {
            long hours = Duration.between(cancelledAt, Instant.parse(occurrence.get("startAt").asString())).toHours();
            int percent = hours >= 72 ? 100 : hours >= 24 ? 50 : 0;
            String rule = hours >= 72 ? "FULL" : hours >= 24 ? "TIER_24_72" : "TIER_UNDER_24";
            if (refundPercentOverride.get() != null) {
                percent = refundPercentOverride.get();
                rule = "OVERRIDE";
            }
            long refund = 100_000L * percent / 100;
            totalRefund += refund;
            totalFee += 100_000L - refund;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("occurrenceId", occurrence.get("occurrenceId").asString());
            item.put("priceAmount", 100_000);
            item.put("paidAmount", 100_000);
            item.put("feeAmount", 100_000L - refund);
            item.put("refundAmount", refund);
            item.put("rule", rule);
            items.add(item);
        }
        writeSuccess(exchange, Map.of("items", items, "totalPaid", 100_000L * items.size(),
                "totalFee", totalFee, "totalRefund", totalRefund));
    }

    private boolean rejected(HttpExchange exchange, AtomicBoolean failing) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
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
