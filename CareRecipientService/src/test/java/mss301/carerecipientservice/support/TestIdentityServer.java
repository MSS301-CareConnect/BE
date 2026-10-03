package mss301.carerecipientservice.support;

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
 * IdentityService giả cho test: JWKS (khóa RSA sinh lúc chạy) + API nội bộ /internal/users/** kiểm X-Internal-Secret.
 * Cho phép tắt (trả 500) để kiểm fallback, và ghi lại header nhận được.
 */
public final class TestIdentityServer {

    public static final String ISSUER = "https://identity.test";
    public static final String INTERNAL_SECRET = "identity-internal-test-secret";
    public static final String KEY_ID = "test-key";

    private static final TestIdentityServer INSTANCE = new TestIdentityServer();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Map<UUID, Map<String, Object>> usersById = new ConcurrentHashMap<>();
    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final AtomicInteger batchCalls = new AtomicInteger();
    private final AtomicReference<String> lastRequestId = new AtomicReference<>();
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
        server.createContext("/internal/users", this::handleInternalUsers);
        server.start();
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

    public void setFailing(boolean value) {
        failing.set(value);
    }

    public int batchCalls() {
        return batchCalls.get();
    }

    public String lastRequestId() {
        return lastRequestId.get();
    }

    // Access token hợp lệ cho service backend: aud chứa careconnect-api, token_type=access.
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
                .expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", tokenType)
                .claim("authz_version", 0)
                .claim("roles", roles)
                .claim("permissions", List.of())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    private void handleJwks(HttpExchange exchange) throws IOException {
        write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString());
    }

    private void handleInternalUsers(HttpExchange exchange) throws IOException {
        lastRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
        if (!INTERNAL_SECRET.equals(exchange.getRequestHeaders().getFirst("X-Internal-Secret"))) {
            write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
            return;
        }
        if (failing.get()) {
            write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/internal/users/batch")) {
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
        if (path.equals("/internal/users/by-email")) {
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
        UUID id = UUID.fromString(path.substring("/internal/users/".length()));
        writeUserOrNotFound(exchange, usersById.get(id));
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
