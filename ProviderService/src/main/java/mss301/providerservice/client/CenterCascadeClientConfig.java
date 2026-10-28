package mss301.providerservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.context.annotation.Bean;

public class CenterCascadeClientConfig {
    @Bean
    public RequestInterceptor centerCascadeAuth(ServiceTokenProvider serviceTokenProvider) {
        return new ServiceTokenInterceptor(serviceTokenProvider);
    }

    @Bean
    public ErrorDecoder centerCascadeErrorDecoder() {
        return (method, response) -> {
            int status = response.status();
            boolean permanent = status >= 400 && status < 500 && status != 408 && status != 429;
            return new AppException(permanent ? ErrorCode.REMOTE_COMMAND_REJECTED : ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        };
    }
}
