package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.InternalServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.InternalServiceQuoteResponse;
import mss301.carecatalogservice.entity.CareService;
import mss301.carecatalogservice.entity.CenterService;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.PricingUnit;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.repository.CareServiceRepository;
import mss301.carecatalogservice.repository.CenterServiceRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.Impl.CatalogQuoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogQuoteServiceTests {

    @Mock
    CareServiceRepository careServiceRepository;

    @Mock
    CenterServiceRepository centerServiceRepository;

    @Mock
    PricePlanRepository pricePlanRepository;

    CatalogQuoteService catalogQuoteService;

    UUID centerId = UUID.randomUUID();
    UUID careServiceId = UUID.randomUUID();
    UUID centerServiceId = UUID.randomUUID();
    UUID pricePlanId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        catalogQuoteService = new CatalogQuoteServiceImpl(
                careServiceRepository,
                centerServiceRepository,
                pricePlanRepository
        );
    }

    @Test
    void quoteSuccessWithExplicitPricePlan() {
        CareService careService = CareService.builder()
                .name("Chăm sóc người cao tuổi")
                .status(ServiceStatus.ACTIVE)
                .build();
        careService.setId(careServiceId);

        CenterService centerService = CenterService.builder()
                .careService(careService)
                .centerId(centerId)
                .status(ServiceStatus.ACTIVE)
                .build();
        centerService.setId(centerServiceId);

        PricePlan pricePlan = PricePlan.builder()
                .centerService(centerService)
                .planName("Gói theo giờ")
                .pricingUnit(PricingUnit.HOUR)
                .durationMinutes(60)
                .basePrice(new BigDecimal("150000.00"))
                .isActive(true)
                .build();
        pricePlan.setId(pricePlanId);

        when(careServiceRepository.findByIdAndDeletedAtIsNull(careServiceId)).thenReturn(Optional.of(careService));
        when(centerServiceRepository.findByIdAndCenterIdAndDeletedAtIsNull(centerServiceId, centerId)).thenReturn(Optional.of(centerService));
        when(pricePlanRepository.findByIdAndCenterServiceIdAndDeletedAtIsNull(pricePlanId, centerServiceId)).thenReturn(Optional.of(pricePlan));

        InternalServiceQuoteRequest request = InternalServiceQuoteRequest.builder()
                .centerId(centerId)
                .items(List.of(InternalServiceQuoteRequest.Item.builder()
                        .careServiceId(careServiceId)
                        .centerServiceId(centerServiceId)
                        .pricePlanId(pricePlanId)
                        .build()))
                .build();

        List<InternalServiceQuoteResponse> result = catalogQuoteService.quote(request);

        assertThat(result).hasSize(1);
        InternalServiceQuoteResponse quote = result.getFirst();
        assertThat(quote.getCareServiceId()).isEqualTo(careServiceId);
        assertThat(quote.getServiceName()).isEqualTo("Chăm sóc người cao tuổi");
        assertThat(quote.getPricingUnit()).isEqualTo(PricingUnit.HOUR);
        assertThat(quote.getBasePrice()).isEqualByComparingTo("150000.00");
    }

    @Test
    void quoteThrowsWhenCareServiceNotFound() {
        when(careServiceRepository.findByIdAndDeletedAtIsNull(careServiceId)).thenReturn(Optional.empty());

        InternalServiceQuoteRequest request = InternalServiceQuoteRequest.builder()
                .centerId(centerId)
                .items(List.of(InternalServiceQuoteRequest.Item.builder()
                        .careServiceId(careServiceId)
                        .build()))
                .build();

        assertThatThrownBy(() -> catalogQuoteService.quote(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CARE_SERVICE_NOT_FOUND);
    }

    @Test
    void quoteThrowsWhenCenterServiceNotFound() {
        CareService careService = CareService.builder()
                .name("Chăm sóc người cao tuổi")
                .status(ServiceStatus.ACTIVE)
                .build();
        careService.setId(careServiceId);

        when(careServiceRepository.findByIdAndDeletedAtIsNull(careServiceId)).thenReturn(Optional.of(careService));
        when(centerServiceRepository.findByCenterIdAndCareServiceIdAndDeletedAtIsNull(centerId, careServiceId))
                .thenReturn(Optional.empty());

        InternalServiceQuoteRequest request = InternalServiceQuoteRequest.builder()
                .centerId(centerId)
                .items(List.of(InternalServiceQuoteRequest.Item.builder()
                        .careServiceId(careServiceId)
                        .build()))
                .build();

        assertThatThrownBy(() -> catalogQuoteService.quote(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CENTER_SERVICE_NOT_FOUND);
    }
}
