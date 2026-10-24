package mss301.providerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
@EnableFeignClients
public class ProviderServiceApplication {

    public static void main(String[] args) {
        // Lưu thời gian theo UTC; frontend tự chuyển sang múi giờ người dùng.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ProviderServiceApplication.class, args);
    }

}
