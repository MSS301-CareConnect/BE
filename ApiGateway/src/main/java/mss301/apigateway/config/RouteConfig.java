package mss301.apigateway.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.filter.GatewayRequestHeaders;
import mss301.apigateway.filter.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.AfterFilterFunctions.removeResponseHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * Định tuyến /api/v1/** → service phía sau (cắt prefix /api/v1, giữ nguyên phần còn lại). Không có route nào tới
 * /internal/** hay /.well-known/jwks.json (service gọi trực tiếp nhau). Cookie Identity đặt Path=/ nên không cần
 * rewrite cookie path.
 */
@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RouteConfig {

    static String OPENAPI_PATH = "/v3/api-docs";

    GatewayProperties gatewayProperties;

    @Bean
    public RouterFunction<ServerResponse> identityRoutes() {
        return route("identity")
                .route(path(GatewayPaths.IDENTITY_ROUTES), http())
                .before(uri(gatewayProperties.getIdentityUrl()))
                .before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))
                .before(GatewayRequestHeaders.sanitize())
                // Gateway đã đặt X-Request-Id ở response — bỏ bản sao từ service để không trùng header.
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> careRecipientRoutes() {
        return route("care-recipient")
                .route(path(GatewayPaths.CARE_RECIPIENT_ROUTES), http())
                .before(uri(gatewayProperties.getCareRecipientUrl()))
                .before(stripPrefix(GatewayPaths.API_PREFIX_SEGMENTS))
                .before(GatewayRequestHeaders.sanitize())
                .after(removeResponseHeader(RequestIdFilter.HEADER))
                .build();
    }

    // Swagger tổng hợp: swagger-ui của gateway đọc OpenAPI của từng service qua các route này.
    @Bean
    public RouterFunction<ServerResponse> apiDocsRoutes() {
        RouterFunction<ServerResponse> identityDocs = route("identity-api-docs")
                .route(path(GatewayPaths.IDENTITY_API_DOCS), http())
                .before(uri(gatewayProperties.getIdentityUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        RouterFunction<ServerResponse> careRecipientDocs = route("care-recipient-api-docs")
                .route(path(GatewayPaths.CARE_RECIPIENT_API_DOCS), http())
                .before(uri(gatewayProperties.getCareRecipientUrl()))
                .before(setPath(OPENAPI_PATH))
                .before(GatewayRequestHeaders.sanitize())
                .build();
        return identityDocs.and(careRecipientDocs);
    }
}
