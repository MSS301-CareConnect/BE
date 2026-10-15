package mss301.bookingservice.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.security.InternalSecretFilter;
import mss301.bookingservice.security.ReadOnlyCookieCsrfTokenRepository;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String CSRF_SCHEME = "csrf";
    private static final String INTERNAL_SCHEME = "internalSecret";
    private static final String INTERNAL_PREFIX = "/internal/";
    private static final String CSRF_DESCRIPTION = "Chỉ cần khi gọi bằng cookie access_token (web qua gateway): "
            + "dán token lấy từ GET /auth/csrf của IdentityService. Gọi bằng Bearer thuần không cần.";
    private static final Set<PathItem.HttpMethod> WRITE_METHODS = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);
    private static final List<String> DOCUMENTED_RESPONSES = List.of("200", "400", "401", "403", "404", "409", "500", "503");
    private static final List<String> GLOBAL_ERROR_RESPONSES = List.of("400", "401", "403", "404", "500", "503");

    static {
        // userId / centerId của người gọi lấy từ token, không phải tham số client gửi → ẩn khỏi Swagger.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class, CurrentCenterId.class);
    }

    @Bean
    public OpenAPI bookingOpenApi(BookingProperties bookingProperties) {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ReadOnlyCookieCsrfTokenRepository.CSRF_HEADER)
                        .description(CSRF_DESCRIPTION))
                .addSecuritySchemes(INTERNAL_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(InternalSecretFilter.HEADER)
                        .description("Chỉ cho /internal/** (service-to-service): giá trị BOOKING_INTERNAL_SECRET."));
        for (String code : DOCUMENTED_RESPONSES) {
            components.addResponses(code, new ApiResponse().description("HTTP " + code + "; business endpoints return APIResponse"));
        }
        OpenAPI openApi = new OpenAPI()
                .info(new Info().title("CareConnect BookingService").version("0.0.1"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
        // Đứng sau gateway (cắt prefix /api/v1): "Try it out" phải gọi qua prefix của gateway; vẫn giữ "/" để gọi trực tiếp.
        String gatewayServerUrl = bookingProperties.getOpenApiServerUrl();
        if (gatewayServerUrl != null && !gatewayServerUrl.isBlank()) {
            openApi.servers(List.of(
                    new Server().url(gatewayServerUrl).description("Qua API gateway"),
                    new Server().url("/").description("Gọi trực tiếp BookingService")));
        }
        return openApi;
    }

    @Bean
    public OpenApiCustomizer globalResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String code : GLOBAL_ERROR_RESPONSES) {
                operation.getResponses().putIfAbsent(code, new ApiResponse().$ref("#/components/responses/" + code));
            }
        }));
    }

    // /internal/** chỉ dùng internal secret; request ghi còn lại: bearer + csrf (csrf chỉ bắt buộc khi dùng cookie).
    @Bean
    public OpenApiCustomizer securityRequirements() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (path.startsWith(INTERNAL_PREFIX)) {
                pathItem.readOperations().forEach(operation -> operation.setSecurity(
                        List.of(new SecurityRequirement().addList(INTERNAL_SCHEME))));
                return;
            }
            pathItem.readOperationsMap().forEach((method, operation) -> {
                if (WRITE_METHODS.contains(method)) {
                    addCsrfRequirement(operation);
                }
            });
        });
    }

    private void addCsrfRequirement(Operation operation) {
        SecurityRequirement requirement = new SecurityRequirement()
                .addList(BEARER_SCHEME)
                .addList(CSRF_SCHEME);
        operation.setSecurity(List.of(requirement));
    }
}
