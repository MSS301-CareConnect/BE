package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.CareCatalogClient;
import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingDetailMapper;
import mss301.bookingservice.service.CareServiceCatalogPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareServiceCatalogPortImpl implements CareServiceCatalogPort {

    CareCatalogClient careCatalogClient;
    BookingDetailMapper bookingDetailMapper;

    @Override
    public List<ServiceQuote> quote(UUID centerId, List<BookingServiceRequest> services) {
        CareCatalogQuoteRequest request = bookingDetailMapper.toCatalogQuoteRequest(centerId, services);
        RemoteApiResponse<List<CareCatalogQuoteResponse>> response = careCatalogClient.quote(request);
        List<CareCatalogQuoteResponse> catalogQuotes = response == null ? null : response.getResult();
        if (catalogQuotes == null || catalogQuotes.isEmpty()) {
            log.warn("CareCatalogService returned an empty quote for center {}", centerId);
            throw new AppException(ErrorCode.CARE_CATALOG_UNAVAILABLE);
        }
        Map<UUID, CareCatalogQuoteResponse> quotesByCareServiceId = catalogQuotes.stream()
                .collect(Collectors.toMap(CareCatalogQuoteResponse::getCareServiceId, Function.identity(),
                        (first, second) -> first));
        List<ServiceQuote> serviceQuotes = new ArrayList<>();
        for (BookingServiceRequest service : services) {
            CareCatalogQuoteResponse catalogQuote = quotesByCareServiceId.get(service.getCareServiceId());
            // Catalog phải báo giá đúng gói khách chọn — thiếu / lệch là dữ liệu không nhất quán, không dùng giá khác.
            if (catalogQuote == null || !service.getPricePlanId().equals(catalogQuote.getPricePlanId())) {
                throw new AppException(ErrorCode.CENTER_SERVICE_NOT_SUPPORTED);
            }
            serviceQuotes.add(bookingDetailMapper.toServiceQuote(catalogQuote));
        }
        return serviceQuotes;
    }
}
