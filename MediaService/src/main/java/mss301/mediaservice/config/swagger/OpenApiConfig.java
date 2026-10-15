package mss301.mediaservice.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import mss301.mediaservice.security.InternalSecretFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mediaOpenApi() {
        SecurityScheme internalSecret = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(InternalSecretFilter.HEADER)
                .description("Giá trị MEDIA_INTERNAL_SECRET, chỉ dùng cho service-to-service.");
        return new OpenAPI()
                .info(new Info()
                        .title("CareConnect MediaService")
                        .version("v1")
                        .description("Metadata file và Cloudflare R2 storage."))
                .components(new Components().addSecuritySchemes("internalSecret", internalSecret));
    }
}
