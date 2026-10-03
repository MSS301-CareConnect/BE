package mss301.carerecipientservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
public class CareRecipientServiceApplication {

    public static void main(String[] args) {
        // Mọi thời điểm lưu/so sánh theo UTC (Instant ↔ timestamptz); FE tự đổi sang múi giờ người dùng.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(CareRecipientServiceApplication.class, args);
    }
}
