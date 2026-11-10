package mss301.bookingservice.client;

import com.sun.net.httpserver.HttpServer;
import feign.RequestTemplate;
import mss301.bookingservice.config.BookingConfig;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.ServiceTokenMapperImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceTokenProviderTests {

    HttpServer identityServer;
    AtomicInteger issuedTokens = new AtomicInteger();
    List<String> requestBodies = new CopyOnWriteArrayList<>();
    volatile int responseStatus = 200;

    @BeforeEach
    void startIdentity() throws IOException {
        identityServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        identityServer.createContext("/api/v1/auth/service-token", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = responseStatus == 200
                    ? "{\"code\":200,\"message\":\"ok\",\"result\":{\"accessToken\":\"token-" + issuedTokens.incrementAndGet()
                    + "\",\"tokenType\":\"Bearer\",\"expiresInSeconds\":600},\"extra\":true}"
                    : "{\"code\":1114,\"message\":\"sai\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        identityServer.setExecutor(Executors.newCachedThreadPool());
        identityServer.start();
    }

    @AfterEach
    void stopIdentity() {
        identityServer.stop(0);
    }

    @Test
    void tokenIsCachedUntilSixtySecondsBeforeExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-22T00:00:00Z"));
        ServiceTokenProvider provider = provider(properties("booking", "secret"), clock);

        assertThat(provider.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofSeconds(539));
        assertThat(provider.getToken()).isEqualTo("token-1");
        clock.advance(Duration.ofSeconds(1));
        assertThat(provider.getToken()).isEqualTo("token-2");
        assertThat(requestBodies.getFirst()).contains("\"clientId\":\"booking\"").contains("\"clientSecret\":\"secret\"");
    }

    @Test
    void concurrentCallersShareOneTokenRequest() throws Exception {
        ServiceTokenProvider provider = provider(properties("booking", "secret"), Clock.systemUTC());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> tokens = executor.invokeAll(Collections.nCopies(16, provider::getToken));
            for (Future<String> token : tokens) {
                assertThat(token.get()).isEqualTo("token-1");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(issuedTokens.get()).isEqualTo(1);
    }

    @Test
    void rejectedOrMissingCredentialsAre503() {
        responseStatus = 401;
        ServiceTokenProvider provider = provider(properties("booking", "wrong"), Clock.systemUTC());
        assertThatThrownBy(provider::getToken)
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.REMOTE_COMMAND_REJECTED);

        int requestsBefore = requestBodies.size();
        ServiceTokenProvider unconfigured = provider(properties("", ""), Clock.systemUTC());
        assertThatThrownBy(unconfigured::getToken).isInstanceOf(AppException.class);
        assertThat(requestBodies).hasSize(requestsBefore);
    }

    @Test
    void interceptorReplacesForwardedUserToken() {
        ServiceTokenProvider provider = provider(properties("booking", "secret"), Clock.systemUTC());
        RequestTemplate template = new RequestTemplate();
        template.header("Authorization", "Bearer user-token");
        new ServiceTokenInterceptor(provider).apply(template);
        assertThat(template.headers().get("Authorization")).containsExactly("Bearer token-1");
    }

    ServiceTokenProvider provider(BookingProperties bookingProperties, Clock clock) {
        return new ServiceTokenProvider(new BookingConfig().serviceTokenRestClient(bookingProperties), bookingProperties,
                new ServiceTokenMapperImpl(), clock);
    }

    BookingProperties properties(String clientId, String clientSecret) {
        BookingProperties bookingProperties = new BookingProperties();
        bookingProperties.getIdentity().setUrl("http://127.0.0.1:" + identityServer.getAddress().getPort());
        bookingProperties.getServiceClient().setClientId(clientId);
        bookingProperties.getServiceClient().setClientSecret(clientSecret);
        return bookingProperties;
    }

    static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
