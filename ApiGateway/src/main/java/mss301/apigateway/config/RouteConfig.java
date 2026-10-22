package mss301.apigateway.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.filter.GatewayRequestHeaders;
import mss301.apigateway.filter.RequestIdFilter;
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

/**
 * Định tuyến /api/v1/** → service phía sau, giữ nguyên path vì mỗi service tự khai báo /api/v1. Gateway là cổng duy
 * nhất cho FE và cho Feign giữa các service. Cookie Identity đặt Path=/ nên không cần rewrite cookie path.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteConfig {

    static String OPENAPI_PATH = "/v3/api-docs";

    GatewayProperties gatewayProperties;

    @Bean
    public RouterFunction<ServerResponse> identityRoutes() {
        return forward("identity", GatewayPaths.IDENTITY_ROUTES, gatewayProperties.getIdentityUrl());
    }

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

    @Bean
    public RouterFunction<ServerResponse> paymentRoutes() {
        return forward("payment", GatewayPaths.PAYMENT_ROUTES, gatewayProperties.getPaymentUrl());
    }

    @Bean
    public RouterFunction<ServerResponse> providerRoutes() {
        return forward("provider", GatewayPaths.PROVIDER_ROUTES, gatewayProperties.getProviderUrl());
    }

    @Bean
    public RouterFunction<ServerResponse> careCatalogRoutes() {
        return forward("care-catalog", GatewayPaths.CARE_CATALOG_ROUTES, gatewayProperties.getCareCatalogUrl());
    }

    @Bean
    public RouterFunction<ServerResponse> mediaRoutes() {
        return forward("media", GatewayPaths.MEDIA_ROUTES, gatewayProperties.getMediaUrl());
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
                .and(docs("payment-api-docs", GatewayPaths.PAYMENT_API_DOCS, gatewayProperties.getPaymentUrl()));
    }

    private RouterFunction<ServerResponse> forward(String routeId, String[] paths, String targetUrl) {
        return route(routeId)
                .route(path(paths), http())
                .before(uri(targetUrl))
                .before(GatewayRequestHeaders.sanitize())
                // Gateway đã đặt X-Request-Id ở response — bỏ bản sao từ service để không trùng header.
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    private RouterFunction<ServerResponse> docs(String routeId, String docsPath, String targetUrl) {
        return route(routeId)
                .route(path(docsPath), http())
                .before(uri(targetUrl))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
    }
}
