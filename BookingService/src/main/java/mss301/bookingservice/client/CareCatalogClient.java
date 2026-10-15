package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "care-catalog", url = "${booking.care-catalog.url}",
        configuration = CareCatalogClientConfig.class, fallbackFactory = CareCatalogClientFallbackFactory.class)
public interface CareCatalogClient {

    @PostMapping("/internal/center-services/quote")
    RemoteApiResponse<List<CareCatalogQuoteResponse>> quote(@RequestBody CareCatalogQuoteRequest request);
}
