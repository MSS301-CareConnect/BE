package mss301.carerecipientservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CareRecipientProperties.class)
public class CareRecipientConfig {
}
