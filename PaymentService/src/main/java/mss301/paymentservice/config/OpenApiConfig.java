package mss301.paymentservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import mss301.paymentservice.security.CurrentCenterId;
import mss301.paymentservice.security.CurrentRoles;
import mss301.paymentservice.security.CurrentUserId;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String BEARER_SCHEME = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class, CurrentCenterId.class, CurrentRoles.class);
    }

    @Bean
    OpenAPI paymentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CareConnect PaymentService")
                        .description("Đặt cọc, thanh toán phần còn lại, hoàn tiền và hóa đơn qua VNPay")
                        .version("0.0.1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token do IdentityService cấp")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
