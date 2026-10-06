package mss301.carecatalogservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CareCatalogProperties.class)
public class CareCatalogConfig {
}
