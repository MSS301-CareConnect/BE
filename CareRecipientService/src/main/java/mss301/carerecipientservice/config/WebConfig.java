package mss301.carerecipientservice.config;

import mss301.carerecipientservice.security.CurrentRolesArgumentResolver;
import mss301.carerecipientservice.security.CurrentUserIdArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserIdArgumentResolver());
        resolvers.add(new CurrentRolesArgumentResolver());
    }
}
