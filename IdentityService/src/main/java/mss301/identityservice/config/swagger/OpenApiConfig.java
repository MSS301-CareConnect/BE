package mss301.identityservice.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import mss301.identityservice.security.CurrentUserId;
import mss301.identityservice.security.SecurityConfig;
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
    private static final String CSRF_DESCRIPTION = "Chỉ cần cho luồng web (cookie) và các endpoint đăng ký / quên mật khẩu. "
            + "Gọi `GET /auth/csrf`, dán `result.token` vào đây. Sau `/auth/login` hoặc `/auth/refresh` phải thay bằng "
            + "`result.csrfToken` mới. Luồng `/auth/mobile/*` và request chỉ dùng Bearer không cần.";
    private static final Set<PathItem.HttpMethod> WRITE_METHODS = Set.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);
    private static final List<String> DOCUMENTED_RESPONSES = List.of("200", "400", "401", "403", "500", "503");
    private static final List<String> GLOBAL_ERROR_RESPONSES = List.of("400", "401", "403", "500", "503");

    static {
        // userId của người gọi lấy từ token, không phải tham số client gửi → ẩn khỏi Swagger.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    @Bean
    public OpenAPI identityOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(CSRF_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(SecurityConfig.CSRF_HEADER)
                        .description(CSRF_DESCRIPTION));
        for (String code : DOCUMENTED_RESPONSES) {
            components.addResponses(code, new ApiResponse().description("HTTP " + code + "; business endpoints return APIResponse"));
        }
        return new OpenAPI()
                .info(new Info().title("CareConnect IdentityService").version("0.0.1"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    @Bean
    public OpenApiCustomizer globalResponses() {
        return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
            for (String code : GLOBAL_ERROR_RESPONSES) {
                operation.getResponses().putIfAbsent(code, new ApiResponse().$ref("#/components/responses/" + code));
            }
        }));
    }

    // Swagger UI chỉ gửi header của scheme có trong security của operation → gắn csrf vào mọi request ghi
    // (trừ /auth/mobile/*), để test được luồng web và đăng ký/quên mật khẩu từ nút Authorize.
    @Bean
    public OpenApiCustomizer csrfRequirements() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) -> {
            if (path.startsWith(SecurityConfig.MOBILE_AUTH_PREFIX)) {
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
        SecurityRequirement requirement = new SecurityRequirement().addList(CSRF_SCHEME);
        // security null = kế thừa bearer toàn cục → giữ bearer; danh sách rỗng = endpoint public → chỉ csrf.
        if (operation.getSecurity() == null) {
            requirement.addList(BEARER_SCHEME);
        }
        operation.setSecurity(List.of(requirement));
    }
}
