package mss301.paymentservice.config;

import mss301.paymentservice.security.CurrentCenterIdArgumentResolver;
import mss301.paymentservice.security.CurrentRolesArgumentResolver;
import mss301.paymentservice.security.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
        resolvers.add(new CurrentCenterIdArgumentResolver());
        resolvers.add(new CurrentRolesArgumentResolver());
    }
}
