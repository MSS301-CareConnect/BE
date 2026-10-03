package mss301.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        // Đồng bộ UTC với các service phía sau (log, thời điểm hết hạn token).
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
