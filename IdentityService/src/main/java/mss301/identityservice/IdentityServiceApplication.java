package mss301.identityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        // Mọi thời điểm lưu/so sánh theo UTC (Instant ↔ timestamptz); FE tự đổi sang múi giờ người dùng.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
