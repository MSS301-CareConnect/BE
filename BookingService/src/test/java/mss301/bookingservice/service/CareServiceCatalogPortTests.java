package mss301.bookingservice.service;

import mss301.bookingservice.client.CareCatalogClient;
import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.enums.UnitType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingDetailMapper;
import mss301.bookingservice.service.Impl.CareServiceCatalogPortImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareServiceCatalogPortTests {

    BookingDetailMapper bookingDetailMapper = Mappers.getMapper(BookingDetailMapper.class);

    @Mock
    CareCatalogClient careCatalogClient;

    CareServiceCatalogPort careServiceCatalogPort;

    UUID centerId = UUID.randomUUID();
    UUID careServiceId = UUID.randomUUID();
    UUID centerServiceId = UUID.randomUUID();
    UUID pricePlanId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        careServiceCatalogPort = new CareServiceCatalogPortImpl(careCatalogClient, bookingDetailMapper);
    }

    @Test
    void quoteSendsChosenPlanAndMapsCatalogPrice() {
        when(careCatalogClient.quote(any(CareCatalogQuoteRequest.class))).thenReturn(response(catalogQuote(pricePlanId)));

        List<ServiceQuote> quotes = careServiceCatalogPort.quote(centerId, List.of(service()));

        ArgumentCaptor<CareCatalogQuoteRequest> request = ArgumentCaptor.forClass(CareCatalogQuoteRequest.class);
        verify(careCatalogClient).quote(request.capture());
        assertThat(request.getValue().getCenterId()).isEqualTo(centerId);
        assertThat(request.getValue().getItems()).singleElement()
                .satisfies(item -> assertThat(item.getPricePlanId()).isEqualTo(pricePlanId));
        ServiceQuote quote = quotes.getFirst();
        assertThat(quote.getServiceName()).isEqualTo("Chăm sóc toàn diện");
        assertThat(quote.getUnitType()).isEqualTo(UnitType.HOUR);
        assertThat(quote.getUnitPrice()).isEqualByComparingTo("100000");
        assertThat(quote.getPlanDurationMinutes()).isEqualTo(60);
        assertThat(quote.getQuantity()).isNull();
        assertThat(quote.getTotalPrice()).isNull();
    }

    @Test
    void quoteRejectsMissingOrDifferentPlan() {
        when(careCatalogClient.quote(any(CareCatalogQuoteRequest.class))).thenReturn(response(catalogQuote(UUID.randomUUID())));
        assertThatThrownBy(() -> careServiceCatalogPort.quote(centerId, List.of(service())))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CENTER_SERVICE_NOT_SUPPORTED);
    }

    @Test
    void emptyCatalogAnswerIs503() {
        when(careCatalogClient.quote(any(CareCatalogQuoteRequest.class))).thenReturn(
                RemoteApiResponse.<List<CareCatalogQuoteResponse>>builder().code(200).result(List.of()).build());
        assertThatThrownBy(() -> careServiceCatalogPort.quote(centerId, List.of(service())))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARE_CATALOG_UNAVAILABLE);
    }

    BookingServiceRequest service() {
        return BookingServiceRequest.builder()
                .careServiceId(careServiceId)
                .centerServiceId(centerServiceId)
                .pricePlanId(pricePlanId)
                .build();
    }

    CareCatalogQuoteResponse catalogQuote(UUID planId) {
        return CareCatalogQuoteResponse.builder()
                .careServiceId(careServiceId)
                .centerServiceId(centerServiceId)
                .pricePlanId(planId)
                .serviceName("Chăm sóc toàn diện")
                .pricingUnit("HOUR")
                .durationMinutes(60)
                .basePrice(new BigDecimal("100000"))
                .active(true)
                .build();
    }

    RemoteApiResponse<List<CareCatalogQuoteResponse>> response(CareCatalogQuoteResponse quote) {
        return RemoteApiResponse.<List<CareCatalogQuoteResponse>>builder().code(200).result(List.of(quote)).build();
    }
}
