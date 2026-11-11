package mss301.apigateway.handler;

import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.apigateway.filter.RequestIdFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SseProxyHandler implements HandlerFunction<ServerResponse> {

    static Set<String> SKIPPED_REQUEST_HEADERS = Set.of("connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    static Set<String> SKIPPED_RESPONSE_HEADERS = Set.of("connection", "keep-alive", "transfer-encoding",
            "content-length", "trailer", "upgrade", ":status", RequestIdFilter.HEADER.toLowerCase(Locale.ROOT));
    static int BUFFER_SIZE = 1024;

    HttpClient httpClient;
    String targetUrl;
    Duration headerTimeout;
    Duration maxStreamDuration;

    // Khởi tạo proxy SSE với các mốc timeout kết nối, header và thời lượng luồng.
    public SseProxyHandler(String targetUrl, Duration connectTimeout, Duration headerTimeout, Duration maxStreamDuration) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.targetUrl = targetUrl;
        this.headerTimeout = headerTimeout;
        this.maxStreamDuration = maxStreamDuration;
    }

    // Mở luồng SSE tới service đích và đẩy sự kiện về client không qua buffer.
    @Override
    public ServerResponse handle(ServerRequest request) throws IOException, InterruptedException {
        HttpRequest upstreamRequest = buildUpstreamRequest(request);
        // Lỗi kết nối / quá thời gian chờ header → IOException → GlobalExceptionHandler trả 503 / 504.
        HttpResponse<InputStream> upstreamResponse = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream());
        return ServerResponse.status(upstreamResponse.statusCode())
                .build((servletRequest, servletResponse) -> {
                    copyResponseHeaders(upstreamResponse, servletResponse);
                    streamBody(upstreamResponse.body(), servletResponse);
                    return null;
                });
    }

    // Dựng request SSE gửi lên service đích kèm header xác thực.
    private HttpRequest buildUpstreamRequest(ServerRequest request) {
        String query = request.uri().getRawQuery();
        URI target = URI.create(targetUrl + request.uri().getRawPath() + (query == null ? "" : "?" + query));
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .GET()
                .timeout(headerTimeout);
        for (Map.Entry<String, List<String>> header : request.headers().asHttpHeaders().headerSet()) {
            if (SKIPPED_REQUEST_HEADERS.contains(header.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String value : header.getValue()) {
                builder.header(header.getKey(), value);
            }
        }
        return builder.build();
    }

    // Chép header response từ service đích sang response trả client.
    private void copyResponseHeaders(HttpResponse<InputStream> upstreamResponse, HttpServletResponse servletResponse) {
        upstreamResponse.headers().map().forEach((name, values) -> {
            if (SKIPPED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                return;
            }
            for (String value : values) {
                servletResponse.addHeader(name, value);
            }
        });
        // Báo reverse proxy phía trước (nginx…) không buffer luồng sự kiện.
        servletResponse.setHeader("X-Accel-Buffering", "no");
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

    // Chép từng phần dữ liệu SSE từ service đích sang client và flush ngay.
    private void streamBody(InputStream upstreamBody, HttpServletResponse servletResponse) throws IOException {
        Instant deadline = Instant.now().plus(maxStreamDuration);
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = upstreamBody) {
            OutputStream output = servletResponse.getOutputStream();
            servletResponse.flushBuffer();
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                output.flush();
                if (Instant.now().isAfter(deadline)) {
                    log.info("SSE stream reached max duration, closing");
                    return;
                }
            }
        } catch (IOException e) {
            // Client đóng tab / upstream ngắt giữa chừng là bình thường với SSE — không biến thành lỗi 5xx.
            log.debug("SSE stream closed ({})", e.getClass().getSimpleName());
        }
    }
}
