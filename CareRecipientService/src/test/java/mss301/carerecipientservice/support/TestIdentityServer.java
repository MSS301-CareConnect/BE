package mss301.carerecipientservice.support;

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * IdentityService giả cho test: JWKS (khóa RSA sinh lúc chạy) + API /api/v1/users/** chuyển tiếp Bearer token.
 * Cho phép tắt (trả 500) để kiểm fallback, và ghi lại header nhận được.
 */
public final class TestIdentityServer {

    public static final String ISSUER = "https://identity.test";
    public static final String KEY_ID = "test-key";
    public static final String SERVICE_CLIENT_ID = "care-recipient";
    public static final String SERVICE_CLIENT_SECRET = "care-recipient-test-secret";

    private static final TestIdentityServer INSTANCE = new TestIdentityServer();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> usersById = new ConcurrentHashMap<>();
    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final AtomicInteger batchCalls = new AtomicInteger();
    private final AtomicReference<String> lastRequestId = new AtomicReference<>();
    private final Set<String> serviceTokens = ConcurrentHashMap.newKeySet();
    private final AtomicInteger serviceTokenCalls = new AtomicInteger();
    private final RSAKey signingKey;
    private final HttpServer server;

    private TestIdentityServer() {
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
        server.createContext("/api/v1/users", this::handleApiUsers);
        server.createContext("/api/v1/auth/service-token", this::handleServiceToken);
        server.start();
    }

    /**
     * Token SERVICE hợp lệ do server giả này phát cho đúng client id/secret test — TestBookingServer dùng để chắc chắn
     * CareRecipient gửi token SERVICE chứ không chuyển tiếp token người dùng.
     */
    public boolean isServiceToken(String token) {
        return serviceTokens.contains(token);
    }

    public int serviceTokenCalls() {
        return serviceTokenCalls.get();
    }

    public static TestIdentityServer get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public String jwksUri() {
        return baseUrl() + "/.well-known/jwks.json";
    }

    public UUID addUser(String email, String fullName, String status, Set<String> roles) {
        UUID id = UUID.randomUUID();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id.toString());
        user.put("email", email.toLowerCase(Locale.ROOT));
        user.put("fullName", fullName);
        user.put("phoneNumber", "0901234567");
        user.put("status", status);
        user.put("roles", roles);
        user.put("permissions", List.of());
        usersById.put(id, user);
        return id;
    }

    public void clear() {
        usersById.clear();
        failing.set(false);
        batchCalls.set(0);
        lastRequestId.set(null);
    }

    public void setFailing(boolean fail) {
        this.failing.set(fail);
    }

    public int batchCalls() {
        return batchCalls.get();
    }

    public int getBatchCalls() {
        return batchCalls.get();
    }

    public String lastRequestId() {
        return lastRequestId.get();
    }

    public String getLastRequestId() {
        return lastRequestId.get();
    }

    public String accessToken(UUID userId, String... roles) {
        return token(userId, List.of("careconnect-identity", "careconnect-api"), "access", ISSUER, List.of(roles));
    }

    public String token(UUID userId, List<String> audience, String tokenType, String issuer, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(userId.toString())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", tokenType)
                .claim("authz_version", 0)
                .claim("roles", roles)
                .claim("permissions", List.of())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    public String issueToken(UUID userId, Set<String> roles) {
        return issueToken(userId, roles, Instant.now().plusSeconds(900));
    }

    public String issueToken(UUID userId, Set<String> roles, Instant expiresAt) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(List.of("careconnect-api", "careconnect-identity"))
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", "access")
                .claim("roles", roles)
                .claim("permissions", List.of())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(KEY_ID)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private void handleJwks(HttpExchange exchange) throws IOException {
        write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
    }

    private void handleApiUsers(HttpExchange exchange) throws IOException {
        lastRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
            return;
        }
        if (failing.get()) {
            write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/api/v1/users/batch")) {
            batchCalls.incrementAndGet();
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            List<Object> found = new ArrayList<>();
            for (JsonNode id : body.get("userIds")) {
                Map<String, Object> user = usersById.get(UUID.fromString(id.asString()));
                if (user != null) {
                    found.add(user);
                }
            }
            writeSuccess(exchange, found);
            return;
        }
        if (path.equals("/api/v1/users/by-email")) {
            String query = exchange.getRequestURI().getRawQuery();
            String email = URLDecoder.decode(query.substring("email=".length()), StandardCharsets.UTF_8)
                    .strip().toLowerCase(Locale.ROOT);
            Map<String, Object> user = usersById.values().stream()
                    .filter(candidate -> candidate.get("email").equals(email))
                    .findFirst()
                    .orElse(null);
            writeUserOrNotFound(exchange, user);
            return;
        }
        UUID id = UUID.fromString(path.substring("/api/v1/users/".length()));
        writeUserOrNotFound(exchange, usersById.get(id));
    }

    private void handleServiceToken(HttpExchange exchange) throws IOException {
        serviceTokenCalls.incrementAndGet();
        JsonNode body = objectMapper.readTree(exchange.getRequestBody());
        if (!SERVICE_CLIENT_ID.equals(body.path("clientId").asString())
                || !SERVICE_CLIENT_SECRET.equals(body.path("clientSecret").asString())) {
            write(exchange, 401, "{\"code\":1114,\"message\":\"Sai thông tin client\"}");
            return;
        }
        String token = token(UUID.randomUUID(), List.of("careconnect-api"), "access", ISSUER, List.of("SERVICE"));
        serviceTokens.add(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", token);
        result.put("tokenType", "Bearer");
        result.put("expiresInSeconds", 600);
        writeSuccess(exchange, result);
    }

    private void writeUserOrNotFound(HttpExchange exchange, Map<String, Object> user) throws IOException {
        if (user == null) {
            write(exchange, 404, "{\"code\":1016,\"message\":\"Người dùng không tồn tại\"}");
            return;
        }
        writeSuccess(exchange, user);
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
}
