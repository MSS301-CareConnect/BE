package mss301.apigateway.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.filter.GatewayRequestHeaders;
import mss301.apigateway.filter.RequestIdFilter;
import mss301.apigateway.handler.LongRequestProxyHandler;
import mss301.apigateway.handler.SseProxyHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;
import static org.springframework.web.servlet.function.RequestPredicates.GET;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteConfig {

    static String OPENAPI_PATH = "/v3/api-docs";

    GatewayProperties gatewayProperties;

    // Định tuyến các API /api/v1 của IdentityService.
    @Bean
    public RouterFunction<ServerResponse> identityRoutes() {
        return forward("identity", GatewayPaths.IDENTITY_ROUTES, gatewayProperties.getIdentityUrl());
    }

    // Định tuyến các API hồ sơ người được chăm sóc sang CareRecipientService.
    @Bean
    public RouterFunction<ServerResponse> careRecipientRoutes() {
        return forward("care-recipient", GatewayPaths.CARE_RECIPIENT_ROUTES, gatewayProperties.getCareRecipientUrl());
    }

    // Route SSE ghép trước route booking chung trong cùng RouterFunction → luôn được khớp trước.
    @Bean
    public RouterFunction<ServerResponse> bookingRoutes(@Value("${spring.http.clients.connect-timeout:2s}") Duration connectTimeout,
                                                        @Value("${spring.http.clients.read-timeout:10s}") Duration readTimeout) {
        SseProxyHandler sseProxyHandler = new SseProxyHandler(gatewayProperties.getBookingUrl(), connectTimeout,
                readTimeout, gatewayProperties.getSseMaxDuration());
        RouterFunction<ServerResponse> notificationStream = route("notification-stream")
                .route(GET(GatewayPaths.NOTIFICATION_STREAM), sseProxyHandler)
                .before(GatewayRequestHeaders.sanitize())
                .build();
        return notificationStream.and(forward("booking", GatewayPaths.BOOKING_ROUTES, gatewayProperties.getBookingUrl()));
    }

    // Định tuyến các API thanh toán, ví, hoàn tiền sang PaymentService.
    @Bean
    public RouterFunction<ServerResponse> paymentRoutes() {
        return forward("payment", GatewayPaths.PAYMENT_ROUTES, gatewayProperties.getPaymentUrl());
    }

    // Định tuyến các API trung tâm, caregiver, ca làm việc sang ProviderService.
    @Bean
    public RouterFunction<ServerResponse> providerRoutes() {
        return forward("provider", GatewayPaths.PROVIDER_ROUTES, gatewayProperties.getProviderUrl());
    }

    // Định tuyến các API danh mục dịch vụ và bảng giá sang CareCatalogService.
    @Bean
    public RouterFunction<ServerResponse> careCatalogRoutes() {
        return forward("care-catalog", GatewayPaths.CARE_CATALOG_ROUTES, gatewayProperties.getCareCatalogUrl());
    }

    // Định tuyến các API upload / tải file sang MediaService.
    @Bean
    public RouterFunction<ServerResponse> mediaRoutes() {
        return forward("media", GatewayPaths.MEDIA_ROUTES, gatewayProperties.getMediaUrl());
    }

    // AIService: proxy riêng với read timeout dài (gateway.ai-read-timeout) thay cho read timeout chung 10s.
    @Bean
    public RouterFunction<ServerResponse> aiRoutes(@Value("${spring.http.clients.connect-timeout:2s}") Duration connectTimeout) {
        LongRequestProxyHandler aiProxyHandler = new LongRequestProxyHandler(gatewayProperties.getAiUrl(), connectTimeout,
                gatewayProperties.getAiReadTimeout());
        return route("ai")
                .route(path(GatewayPaths.AI_ROUTES), aiProxyHandler)
                .before(GatewayRequestHeaders.sanitize())
                .build();
    }

    // Swagger tổng hợp: swagger-ui của gateway đọc OpenAPI của từng service qua các route này.
    @Bean
    public RouterFunction<ServerResponse> apiDocsRoutes() {
        return docs("identity-api-docs", GatewayPaths.IDENTITY_API_DOCS, gatewayProperties.getIdentityUrl())
                .and(docs("care-recipient-api-docs", GatewayPaths.CARE_RECIPIENT_API_DOCS,
                        gatewayProperties.getCareRecipientUrl()))
                .and(docs("booking-api-docs", GatewayPaths.BOOKING_API_DOCS, gatewayProperties.getBookingUrl()))
                .and(docs("provider-api-docs", GatewayPaths.PROVIDER_API_DOCS, gatewayProperties.getProviderUrl()))
                .and(docs("care-catalog-api-docs", GatewayPaths.CARE_CATALOG_API_DOCS,
                        gatewayProperties.getCareCatalogUrl()))
                .and(docs("media-api-docs", GatewayPaths.MEDIA_API_DOCS, gatewayProperties.getMediaUrl()))
                .and(docs("payment-api-docs", GatewayPaths.PAYMENT_API_DOCS, gatewayProperties.getPaymentUrl()))
                .and(docs("ai-api-docs", GatewayPaths.AI_API_DOCS, gatewayProperties.getAiUrl()));
    }

    // Tạo route chuyển tiếp nguyên request tới service đích cho danh sách path.
    private RouterFunction<ServerResponse> forward(String routeId, String[] paths, String targetUrl) {
        return route(routeId)
                .route(path(paths), http())
                .before(uri(targetUrl))
                .before(GatewayRequestHeaders.sanitize())
                // Gateway đã đặt X-Request-Id ở response — bỏ bản sao từ service để không trùng header.
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    // Tạo route chuyển tiếp trang tài liệu OpenAPI của một service.
    private RouterFunction<ServerResponse> docs(String routeId, String docsPath, String targetUrl) {
        return route(routeId)
                .route(path(docsPath), http())
                .before(uri(targetUrl))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
    }
}
