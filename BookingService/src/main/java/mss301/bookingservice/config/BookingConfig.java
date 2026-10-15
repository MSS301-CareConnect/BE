package mss301.bookingservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(BookingProperties.class)
public class BookingConfig {

    // Một nguồn "bây giờ" cho sinh lịch / hạn duyệt / hạn cọc — test thay bằng Clock cố định.
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
