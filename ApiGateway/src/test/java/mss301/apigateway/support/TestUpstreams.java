package mss301.apigateway.support;

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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * IdentityService + CareRecipientService giả: echo lại path và header nhận được (tên header viết thường) để test
 * kiểm gateway chuyển tiếp gì. Identity giả còn có JWKS, /auth/login đặt cookie như Identity thật (Path=/), và
 * /users/slow ngủ lâu để test timeout.
 */
public final class TestUpstreams {

    public static final String ISSUER = "https://identity.test";
    public static final String KEY_ID = "gateway-test-key";

    private static final TestUpstreams INSTANCE = new TestUpstreams();

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final List<String> receivedPaths = new CopyOnWriteArrayList<>();
    private final RSAKey signingKey;
    private final HttpServer identityServer;
    private final HttpServer careRecipientServer;
    private final HttpServer bookingServer;
    private final HttpServer providerServer;
    private final HttpServer careCatalogServer;
    private final HttpServer mediaServer;
    private final HttpServer paymentServer;

    private TestUpstreams() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            signingKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(KEY_ID)
                    .build();
            identityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            careRecipientServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            bookingServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            careCatalogServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            mediaServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            paymentServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
        identityServer.createContext("/.well-known/jwks.json",
                exchange -> write(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString(), Map.of()));
        identityServer.createContext("/api/v1/auth/login", this::handleLogin);
        identityServer.createContext("/api/v1/users/slow", this::handleSlow);
        identityServer.createContext("/v3/api-docs", exchange -> write(exchange, 200,
                "{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"CareConnect IdentityService\"}}", Map.of()));
        identityServer.createContext("/", exchange -> echo(exchange, "identity"));
        careRecipientServer.createContext("/", exchange -> echo(exchange, "care-recipient"));
        bookingServer.createContext("/api/v1/notifications/stream", this::handleStream);
        bookingServer.createContext("/", exchange -> echo(exchange, "booking"));
        providerServer.createContext("/", exchange -> echo(exchange, "provider"));
        careCatalogServer.createContext("/", exchange -> echo(exchange, "care-catalog"));
        mediaServer.createContext("/", exchange -> echo(exchange, "media"));
        paymentServer.createContext("/", exchange -> echo(exchange, "payment"));
        // Nhiều thread: /users/slow không được chặn các request khác (JWKS…).
        identityServer.setExecutor(Executors.newCachedThreadPool());
        careRecipientServer.setExecutor(Executors.newCachedThreadPool());
        bookingServer.setExecutor(Executors.newCachedThreadPool());
        providerServer.setExecutor(Executors.newCachedThreadPool());
        careCatalogServer.setExecutor(Executors.newCachedThreadPool());
        mediaServer.setExecutor(Executors.newCachedThreadPool());
        paymentServer.setExecutor(Executors.newCachedThreadPool());
        bookingServer.start();
        providerServer.start();
        careCatalogServer.start();
        mediaServer.start();
        paymentServer.start();
        identityServer.start();
        careRecipientServer.start();
    }

    public static TestUpstreams get() {
        return INSTANCE;
    }

    public String identityUrl() {
        return "http://127.0.0.1:" + identityServer.getAddress().getPort();
    }

    public String careRecipientUrl() {
        return "http://127.0.0.1:" + careRecipientServer.getAddress().getPort();
    }

    public String bookingUrl() {
        return "http://127.0.0.1:" + bookingServer.getAddress().getPort();
    }

    public String providerUrl() {
        return "http://127.0.0.1:" + providerServer.getAddress().getPort();
    }

    public String careCatalogUrl() {
        return "http://127.0.0.1:" + careCatalogServer.getAddress().getPort();
    }

    public String mediaUrl() {
        return "http://127.0.0.1:" + mediaServer.getAddress().getPort();
    }

    public String paymentUrl() {
        return "http://127.0.0.1:" + paymentServer.getAddress().getPort();
    }

    public String jwksUri() {
        return identityUrl() + "/.well-known/jwks.json";
    }

    public List<String> receivedPaths() {
        return receivedPaths;
    }

    public String accessToken(UUID userId, String... roles) {
        return token(userId, List.of("careconnect-identity", "careconnect-api"), "access", List.of(roles));
    }

    public String token(UUID userId, List<String> audience, String tokenType, List<String> roles) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("token_type", tokenType)
                .claim("authz_version", 0)
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims);
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        return jwt.serialize();
    }

    // Giống IdentityService thật: 3 cookie HttpOnly, Path=/, không Domain.
    private void handleLogin(HttpExchange exchange) throws IOException {
        receivedPaths.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Set-Cookie", "access_token=new-access; Path=/; Max-Age=900; HttpOnly; SameSite=Lax");
        exchange.getResponseHeaders().add("Set-Cookie", "refresh_token=new-refresh; Path=/; Max-Age=604800; HttpOnly; SameSite=Lax");
        exchange.getResponseHeaders().add("Set-Cookie", "XSRF-TOKEN=new-csrf; Path=/; HttpOnly; SameSite=Lax");
        write(exchange, 200, "{\"code\":200,\"message\":\"Đăng nhập thành công\",\"result\":{\"csrfToken\":\"new-csrf\"}}", Map.of());
    }

    // SSE giả: hai sự kiện cách nhau lâu hơn read timeout của test (1s), echo lại Last-Event-ID và Authorization.
    private void handleStream(HttpExchange exchange) throws IOException {
        receivedPaths.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("X-Request-Id", "upstream-id-should-be-removed");
        exchange.sendResponseHeaders(200, 0);
        String lastEventId = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(("id: 1\nevent: heartbeat\ndata: last=" + lastEventId + "\n\n").getBytes(StandardCharsets.UTF_8));
            output.flush();
            Thread.sleep(1500);
            output.write(("id: 2\nevent: notification\ndata: auth=" + (authorization != null) + "\n\n").getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSlow(HttpExchange exchange) throws IOException {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        write(exchange, 200, "{\"code\":200}", Map.of());
    }

    private void echo(HttpExchange exchange, String service) throws IOException {
        receivedPaths.add(exchange.getRequestURI().getPath());
        Map<String, Object> headers = new LinkedHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), String.join(",", values)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("service", service);
        result.put("method", exchange.getRequestMethod());
        result.put("path", exchange.getRequestURI().getPath());
        result.put("query", exchange.getRequestURI().getRawQuery());
        result.put("headers", headers);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", 200);
        envelope.put("message", "echo");
        envelope.put("result", result);
        // Giống service thật: trả X-Request-Id riêng (gateway phải bỏ để không trùng header).
        write(exchange, 200, objectMapper.writeValueAsString(envelope), Map.of("X-Request-Id", "upstream-id-should-be-removed"));
    }

    private void write(HttpExchange exchange, int status, String body, Map<String, String> extraHeaders) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        extraHeaders.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
