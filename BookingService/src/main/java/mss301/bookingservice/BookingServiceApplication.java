package mss301.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        // Mọi thời điểm lưu/so sánh theo UTC (Instant ↔ timestamptz); lịch được sinh theo booking.schedule.timezone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
