package mss301.carerecipientservice.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BookingService giả cho test: GET /api/v1/bookings/caregiver-access chỉ nhận token SERVICE, trả allowed theo các cặp
 * (caregiverUserId, recipientId) đã đăng ký. Có thể tắt (500) để kiểm fail-closed.
 */
public final class TestBookingServer {

    private static final TestBookingServer INSTANCE = new TestBookingServer();

    private final Set<String> assignments = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final List<String> notifications = new CopyOnWriteArrayList<>();
    private final Map<String, Long> unfinished = new ConcurrentHashMap<>();
    private final AtomicInteger rejectedUserTokens = new AtomicInteger();
    private final HttpServer server;

    private TestBookingServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        server.createContext("/api/v1/bookings/caregiver-access", this::handleCaregiverAccess);
        server.createContext("/api/v1/notifications", this::handleNotification);
        server.createContext("/api/v1/bookings/recipient-activity", this::handleRecipientActivity);
        server.start();
    }

    public static TestBookingServer get() {
        return INSTANCE;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void assign(UUID caregiverUserId, UUID recipientId) {
        assignments.add(caregiverUserId + ":" + recipientId);
    }

    public void unassign(UUID caregiverUserId, UUID recipientId) {
        assignments.remove(caregiverUserId + ":" + recipientId);
    }

    public void setFailing(boolean value) {
        failing.set(value);
    }

    public int rejectedUserTokens() {
        return rejectedUserTokens.get();
    }

    private void handleCaregiverAccess(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
            return;
        }
        // Như BookingService thật: caregiver-access chỉ nhận token SERVICE (hasRole('SERVICE')), token người dùng → 403.
        if (!TestIdentityServer.get().isServiceToken(authHeader.substring("Bearer ".length()))) {
            rejectedUserTokens.incrementAndGet();
            write(exchange, 403, "{\"code\":403,\"message\":\"Bạn không có quyền truy cập\"}");
            return;
        }
        if (failing.get()) {
            write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
            return;
        }
        Map<String, String> query = new HashMap<>();
        for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(parts[0], parts[1]);
        }
        String caregiverUserId = query.get("caregiverUserId");
        String recipientId = query.get("recipientId");
        boolean allowed = assignments.contains(caregiverUserId + ":" + recipientId);
        write(exchange, 200, "{\"code\":200,\"message\":\"OK\",\"result\":{\"caregiverUserId\":\"%s\",\"recipientId\":\"%s\",\"allowed\":%s}}"
                .formatted(caregiverUserId, recipientId, allowed));
    }

    // POST /api/v1/notifications (token SERVICE) — ghi lại body để test kiểm người được mời nhận thông báo.
    private void handleNotification(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !TestIdentityServer.get().isServiceToken(authHeader.substring("Bearer ".length()))) {
            write(exchange, 403, "{\"code\":403}");
            return;
        }
        notifications.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        write(exchange, 200, "{\"code\":200,\"message\":\"OK\",\"result\":{}}");
    }

    // M-07: người bệnh còn booking chưa kết thúc (mặc định không có).
    public void setUnfinishedBookings(UUID recipientId, long count) {
        unfinished.put(recipientId.toString(), count);
    }

    private void handleRecipientActivity(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !TestIdentityServer.get().isServiceToken(authHeader.substring("Bearer ".length()))) {
            write(exchange, 403, "{\"code\":403}");
            return;
        }
        if (failing.get()) {
            write(exchange, 500, "{\"code\":9999,\"message\":\"Uncategorized error\"}");
            return;
        }
        String recipientId = exchange.getRequestURI().getRawQuery().split("recipientId=", 2)[1].split("&")[0];
        write(exchange, 200, "{\"code\":200,\"message\":\"OK\",\"result\":{\"recipientId\":\"%s\",\"unfinishedBookings\":%d}}"
                .formatted(recipientId, unfinished.getOrDefault(recipientId, 0L)));
    }

    public List<String> notifications() {
        return notifications;
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
