package mss301.bookingservice.client;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import mss301.bookingservice.config.BookingProperties;
import org.springframework.context.annotation.Bean;

public class CareCatalogClientConfig {

    @Bean
    public RequestInterceptor careCatalogRequestInterceptor(BookingProperties bookingProperties) {
        return InternalClientHeaders.interceptor(bookingProperties.getCareCatalog().getInternalSecret());
    }

    @Bean
    public ErrorDecoder careCatalogErrorDecoder() {
        return new CareCatalogErrorDecoder();
    }
}
