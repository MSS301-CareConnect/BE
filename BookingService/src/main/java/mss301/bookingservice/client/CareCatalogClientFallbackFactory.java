package mss301.bookingservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class CareCatalogClientFallbackFactory implements FallbackFactory<CareCatalogClient> {

    @Override
    public CareCatalogClient create(Throwable cause) {
        return new CareCatalogClient() {
            @Override
            public RemoteApiResponse<List<CareCatalogQuoteResponse>> quote(CareCatalogQuoteRequest request) {
                if (cause instanceof AppException appException) {
                    throw appException;
                }
                log.warn("CareCatalogService call failed ({})", cause.getClass().getSimpleName());
                throw new AppException(ErrorCode.CARE_CATALOG_UNAVAILABLE);
            }
        };
    }
}
