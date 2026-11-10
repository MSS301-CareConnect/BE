package mss301.apigateway.handler;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.filter.RequestIdFilter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Proxy cho route cần chờ lâu hơn read timeout chung (10s) của gateway — hiện là AIService (/api/v1/ai/**): Groq có thể
 * mất tới hàng chục giây cho một câu trả lời hoặc một lần đọc ảnh chứng chỉ. Handler chép nguyên method, header, body
 * (kể cả multipart — GatewayMvcMultipartResolver không tiêu thụ body) và trả nguyên status/header/body của service.
 * Request đã qua SecurityConfig (JWT) và GatewayRequestHeaders.sanitize() như mọi route khác.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LongRequestProxyHandler implements HandlerFunction<ServerResponse> {

    static Set<String> SKIPPED_REQUEST_HEADERS = Set.of("connection", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    static Set<String> SKIPPED_RESPONSE_HEADERS = Set.of("connection", "keep-alive", "transfer-encoding",
            "content-length", "trailer", "upgrade", ":status", RequestIdFilter.HEADER.toLowerCase(Locale.ROOT));

    HttpClient httpClient;
    String targetUrl;
    Duration readTimeout;

    public LongRequestProxyHandler(String targetUrl, Duration connectTimeout, Duration readTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.targetUrl = targetUrl;
        this.readTimeout = readTimeout;
    }

    @Override
    public ServerResponse handle(ServerRequest request) throws IOException, InterruptedException {
        HttpRequest upstreamRequest = buildUpstreamRequest(request);
        // Lỗi kết nối / quá readTimeout → IOException → GlobalExceptionHandler trả 503 / 504 như route thường.
        HttpResponse<byte[]> upstreamResponse = httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = upstreamResponse.body();
        return ServerResponse.status(HttpStatusCode.valueOf(upstreamResponse.statusCode()))
                .headers(headers -> upstreamResponse.headers().map().forEach((name, values) -> {
                    if (!SKIPPED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                        values.forEach(value -> headers.add(name, value));
                    }
                }))
                .build((servletRequest, servletResponse) -> {
                    if (body.length > 0) {
                        servletResponse.setContentLength(body.length);
                        servletResponse.getOutputStream().write(body);
                    }
                    return null;
                });
    }

    private HttpRequest buildUpstreamRequest(ServerRequest request) throws IOException {
        String query = request.uri().getRawQuery();
        URI target = URI.create(targetUrl + request.uri().getRawPath() + (query == null ? "" : "?" + query));
        byte[] body = readBody(request);
        HttpRequest.BodyPublisher publisher = body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .method(request.method().name(), publisher)
                .timeout(readTimeout);
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

    private byte[] readBody(ServerRequest request) throws IOException {
        try (InputStream input = request.servletRequest().getInputStream()) {
            return input.readAllBytes();
        }
    }
}
