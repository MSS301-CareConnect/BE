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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BookingService giả cho test: GET /api/v1/bookings/caregiver-access kiểm Bearer token, trả allowed theo các cặp
 * (caregiverUserId, recipientId) đã đăng ký. Có thể tắt (500) để kiểm fail-closed.
 */
public final class TestBookingServer {

    private static final TestBookingServer INSTANCE = new TestBookingServer();

    private final Set<String> assignments = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean failing = new AtomicBoolean(false);
    private final HttpServer server;

    private TestBookingServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        server.createContext("/api/v1/bookings/caregiver-access", this::handleCaregiverAccess);
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

    private void handleCaregiverAccess(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            write(exchange, 401, "{\"code\":401,\"message\":\"Bạn chưa được xác thực\"}");
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

    private void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
