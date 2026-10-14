package mss301.bookingservice.service;

import mss301.bookingservice.client.CareCatalogClient;
import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareServiceCatalogPortTests {

    BookingDetailMapper bookingDetailMapper = Mappers.getMapper(BookingDetailMapper.class);

    @Mock
    CareCatalogClient careCatalogClient;

    BookingProperties bookingProperties;

    CareServiceCatalogPort careServiceCatalogPort;

    UUID centerId = UUID.randomUUID();
    UUID careServiceId = UUID.randomUUID();
    UUID pricePlanId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        bookingProperties = new BookingProperties();
        careServiceCatalogPort = new CareServiceCatalogPortImpl(bookingDetailMapper, bookingProperties, careCatalogClient);
    }

    @Test
    void quoteUnpricedWhenIntegrationDisabled() {
        bookingProperties.getIntegration().setCatalogEnabled(false);

        List<BookingServiceRequest> services = List.of(
                BookingServiceRequest.builder().careServiceId(careServiceId).build()
        );
        List<OccurrenceSlot> slots = List.of(
                OccurrenceSlot.builder().occurrenceNo(1).durationMinutes(120).startAt(Instant.now()).endAt(Instant.now().plusSeconds(7200)).build()
        );

        BookingQuote quote = careServiceCatalogPort.quote(centerId, services, slots);

        assertThat(quote.getItems()).hasSize(1);
        ServiceQuote item = quote.getItems().getFirst();
        assertThat(item.getCareServiceId()).isEqualTo(careServiceId);
        assertThat(item.getServiceName()).isNull();
        assertThat(item.getUnitPrice()).isNull();
        assertThat(quote.getSubtotal()).isNull();
        assertThat(quote.getDepositAmount()).isNull();
    }

    @Test
    void quoteSuccessWhenIntegrationEnabledWithHourlyPlan() {
        bookingProperties.getIntegration().setCatalogEnabled(true);
        bookingProperties.setDepositRate(new BigDecimal("0.30"));

        List<BookingServiceRequest> services = List.of(
                BookingServiceRequest.builder().careServiceId(careServiceId).pricePlanId(pricePlanId).build()
        );
        // 2 buổi, mỗi buổi 120 phút = tổng 240 phút = 4 giờ (plan 60 phút)
        List<OccurrenceSlot> slots = List.of(
                OccurrenceSlot.builder().occurrenceNo(1).durationMinutes(120).startAt(Instant.now()).endAt(Instant.now().plusSeconds(7200)).build(),
                OccurrenceSlot.builder().occurrenceNo(2).durationMinutes(120).startAt(Instant.now().plusSeconds(86400)).endAt(Instant.now().plusSeconds(93600)).build()
        );

        CareCatalogQuoteResponse catalogResponse = CareCatalogQuoteResponse.builder()
                .careServiceId(careServiceId)
                .pricePlanId(pricePlanId)
                .serviceName("Chăm sóc toàn diện")
                .pricingUnit("HOUR")
                .durationMinutes(60)
                .basePrice(new BigDecimal("100000.00"))
                .active(true)
                .build();

        RemoteApiResponse<List<CareCatalogQuoteResponse>> apiResponse = RemoteApiResponse.<List<CareCatalogQuoteResponse>>builder()
                .code(200)
                .result(List.of(catalogResponse))
                .build();

        when(careCatalogClient.quote(any(CareCatalogQuoteRequest.class))).thenReturn(apiResponse);

        BookingQuote quote = careServiceCatalogPort.quote(centerId, services, slots);

        assertThat(quote.getItems()).hasSize(1);
        ServiceQuote item = quote.getItems().getFirst();
        assertThat(item.getCareServiceId()).isEqualTo(careServiceId);
        assertThat(item.getServiceName()).isEqualTo("Chăm sóc toàn diện");
        assertThat(item.getUnitType()).isEqualTo(UnitType.HOUR);
        assertThat(item.getQuantity()).isEqualTo(4); // 240 min / 60 min = 4
        assertThat(item.getUnitPrice()).isEqualByComparingTo("100000.00");
        assertThat(item.getTotalPrice()).isEqualByComparingTo("400000.00");

        assertThat(quote.getSubtotal()).isEqualByComparingTo("400000.00");
        assertThat(quote.getDepositAmount()).isEqualByComparingTo("120000.00"); // 30% of 400,000
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("400000.00");
    }

    @Test
    void quoteThrowsWhenCatalogReturnsEmpty() {
        bookingProperties.getIntegration().setCatalogEnabled(true);

        List<BookingServiceRequest> services = List.of(
                BookingServiceRequest.builder().careServiceId(careServiceId).build()
        );
        List<OccurrenceSlot> slots = List.of(
                OccurrenceSlot.builder().occurrenceNo(1).durationMinutes(60).startAt(Instant.now()).endAt(Instant.now().plusSeconds(3600)).build()
        );

        RemoteApiResponse<List<CareCatalogQuoteResponse>> apiResponse = RemoteApiResponse.<List<CareCatalogQuoteResponse>>builder()
                .code(200)
                .result(List.of())
                .build();

        when(careCatalogClient.quote(any(CareCatalogQuoteRequest.class))).thenReturn(apiResponse);

        assertThatThrownBy(() -> careServiceCatalogPort.quote(centerId, services, slots))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARE_CATALOG_UNAVAILABLE);
    }
}
