package mss301.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
public class AiServiceApplication {

    // Khởi động AIService với múi giờ mặc định UTC.
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AiServiceApplication.class, args);
    }

}
