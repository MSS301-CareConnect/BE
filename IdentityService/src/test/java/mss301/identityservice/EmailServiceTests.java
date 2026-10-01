package mss301.identityservice;

import com.sun.net.httpserver.HttpServer;
import mss301.identityservice.config.BrevoConfig;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.exception.*;
import mss301.identityservice.service.Impl.EmailServiceImpl;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class EmailServiceTests {
    private EmailServiceImpl emailService(IdentityProperties properties) {
        return new EmailServiceImpl(properties, new BrevoConfig().brevoRestClient(properties));
    }
    private IdentityProperties config(int port) {
        IdentityProperties properties = new IdentityProperties();
        properties.getBrevo().setApiKey("test-key");
        properties.getBrevo().setSenderEmail("sender@example.test");
        properties.getBrevo().setBaseUrl("http://localhost:" + port);
        return properties;
    }
    @Test void sendsBrevoJsonAndApiKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> payload = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        server.createContext("/smtp/email", exchange -> {
            payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("api-key"));
            exchange.sendResponseHeaders(201, -1); exchange.close();
        });
        server.start();
        try {
            emailService(config(server.getAddress().getPort())).sendOtp("recipient@example.test", "123456");
            assertThat(key.get()).isEqualTo("test-key");
            assertThat(payload.get()).contains("recipient@example.test", "sender@example.test", "123456", "textContent");
        } finally { server.stop(0); }
    }
    @Test void providerFailureReturnsFriendlyError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/smtp/email", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            assertThatThrownBy(() -> emailService(config(server.getAddress().getPort())).sendOtp("recipient@example.test", "123456"))
                    .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.EMAIL_DELIVERY_FAILED);
        } finally { server.stop(0); }
    }
    @Test void missingCredentialsDoesNotAttemptDelivery() {
        assertThatThrownBy(() -> emailService(new IdentityProperties()).sendOtp("recipient@example.test", "123456"))
                .isInstanceOf(AppException.class).extracting("errorCode").isEqualTo(ErrorCode.EMAIL_DELIVERY_FAILED);
    }
}
