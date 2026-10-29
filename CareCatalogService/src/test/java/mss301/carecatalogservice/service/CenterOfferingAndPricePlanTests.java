package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.PricePlanResponse;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.entity.CenterOffering;
import mss301.carecatalogservice.entity.PricePlan;
import mss301.carecatalogservice.enums.PricingUnit;
import mss301.carecatalogservice.enums.ServiceStatus;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.exception.GlobalExceptionHandler;
import mss301.carecatalogservice.mapper.CatalogItemMapperImpl;
import mss301.carecatalogservice.mapper.CenterOfferingMapperImpl;
import mss301.carecatalogservice.mapper.PricePlanMapperImpl;
import mss301.carecatalogservice.repository.CatalogItemRepository;
import mss301.carecatalogservice.repository.CenterOfferingRepository;
import mss301.carecatalogservice.repository.PricePlanRepository;
import mss301.carecatalogservice.service.Impl.CenterOfferingServiceImpl;
import mss301.carecatalogservice.service.Impl.PricePlanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CenterOfferingAndPricePlanTests {

    @Mock
    CatalogItemRepository catalogItemRepository;

    @Mock
    CenterOfferingRepository centerOfferingRepository;

    @Mock
    PricePlanRepository pricePlanRepository;

    @Mock
    CatalogQuoteService catalogQuoteService;

    CenterOfferingServiceImpl centerOfferingService;
    PricePlanServiceImpl pricePlanService;

    UUID centerId = UUID.randomUUID();
    UUID catalogItemId = UUID.randomUUID();
    UUID offeringId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        centerOfferingService = new CenterOfferingServiceImpl(centerOfferingRepository, catalogItemRepository, pricePlanRepository,
                new CenterOfferingMapperImpl(new CatalogItemMapperImpl(), new PricePlanMapperImpl()), catalogQuoteService);
        pricePlanService = new PricePlanServiceImpl(pricePlanRepository, centerOfferingRepository, new PricePlanMapperImpl());
    }

    @Test
    void offeringCannotUseInactiveCatalogItem() {
        CatalogItem inactiveItem = CatalogItem.builder().name("Tắm gội").category("Vệ sinh").status(ServiceStatus.INACTIVE).build();
        inactiveItem.setId(catalogItemId);
        when(catalogItemRepository.findByIdAndDeletedAtIsNull(catalogItemId)).thenReturn(Optional.of(inactiveItem));
        CenterOfferingRequest request = CenterOfferingRequest.builder()
                .catalogItemId(catalogItemId)
                .status(ServiceStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> centerOfferingService.create(centerId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERVICE_NOT_ACTIVE);
        verify(centerOfferingRepository, never()).save(any());
    }

    @Test
    void offeringWithInactiveItemCanOnlyBeSwitchedOff() {
        CatalogItem inactiveItem = CatalogItem.builder().name("Tắm gội").category("Vệ sinh").status(ServiceStatus.INACTIVE).build();
        inactiveItem.setId(catalogItemId);
        CenterOffering offering = CenterOffering.builder().catalogItem(inactiveItem).centerId(centerId)
                .status(ServiceStatus.ACTIVE).build();
        offering.setId(offeringId);
        when(centerOfferingRepository.findByIdAndDeletedAtIsNull(offeringId)).thenReturn(Optional.of(offering));
        when(centerOfferingRepository.saveAndFlush(any(CenterOffering.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> centerOfferingService.update(centerId, offeringId, CenterOfferingRequest.builder()
                .catalogItemId(catalogItemId).status(ServiceStatus.ACTIVE).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SERVICE_NOT_ACTIVE);
        centerOfferingService.update(centerId, offeringId, CenterOfferingRequest.builder()
                .catalogItemId(catalogItemId).status(ServiceStatus.INACTIVE).build());
        assertThat(offering.getStatus()).isEqualTo(ServiceStatus.INACTIVE);
    }

    @Test
    void pricePlanIsActiveByDefaultAndKeepsFlagOnUpdate() {
        CenterOffering offering = activeOffering();
        when(centerOfferingRepository.findByIdAndDeletedAtIsNull(offeringId)).thenReturn(Optional.of(offering));
        when(pricePlanRepository.save(any(PricePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        APIResponse<PricePlanResponse> created = pricePlanService.create(centerId, planRequest(PricingUnit.HOUR, 60, null));
        assertThat(created.getResult().getIsActive()).isTrue();

        PricePlan inactivePlan = PricePlan.builder().centerOffering(offering).planName("Cũ").pricingUnit(PricingUnit.DAY)
                .durationMinutes(480).basePrice(BigDecimal.ONE).isActive(false).build();
        UUID planId = UUID.randomUUID();
        inactivePlan.setId(planId);
        when(pricePlanRepository.findByIdAndDeletedAtIsNull(planId)).thenReturn(Optional.of(inactivePlan));
        when(pricePlanRepository.saveAndFlush(any(PricePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        APIResponse<PricePlanResponse> updated = pricePlanService.update(centerId, planId, planRequest(PricingUnit.DAY, 480, null));
        assertThat(updated.getResult().getIsActive()).isFalse();
        assertThat(updated.getResult().getPlanName()).isEqualTo("Gói");
    }

    @Test
    void hourlyPlanMustBeSixtyMinutes() {
        assertThatThrownBy(() -> pricePlanService.create(centerId, planRequest(PricingUnit.HOUR, 90, true)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DURATION);
        verify(pricePlanRepository, never()).save(any());

        CenterOffering offering = activeOffering();
        when(centerOfferingRepository.findByIdAndDeletedAtIsNull(offeringId)).thenReturn(Optional.of(offering));
        when(pricePlanRepository.save(any(PricePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(pricePlanService.create(centerId, planRequest(PricingUnit.SHIFT, 240, true)).getResult().getDurationMinutes())
                .isEqualTo(240);
    }

    @Test
    void duplicateCenterServiceRaceMapsToConflict() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("insert failed",
                new SQLException("duplicate key value violates unique constraint \"uk_center_services_center_care\""));
        ResponseEntity<APIResponse<Void>> response = new GlobalExceptionHandler().handlingDataIntegrityViolation(violation);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS.getCode());

        ResponseEntity<APIResponse<Void>> otherResponse = new GlobalExceptionHandler().handlingDataIntegrityViolation(
                new DataIntegrityViolationException("fk", new SQLException("violates foreign key constraint")));
        assertThat(otherResponse.getBody().getCode()).isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
    }

    CenterOffering activeOffering() {
        CatalogItem item = CatalogItem.builder().name("Tắm gội").category("Vệ sinh").status(ServiceStatus.ACTIVE).build();
        item.setId(catalogItemId);
        CenterOffering offering = CenterOffering.builder().catalogItem(item).centerId(centerId).status(ServiceStatus.ACTIVE).build();
        offering.setId(offeringId);
        return offering;
    }

    PricePlanRequest planRequest(PricingUnit unit, int durationMinutes, Boolean active) {
        return PricePlanRequest.builder()
                .centerOfferingId(offeringId)
                .planName("Gói")
                .pricingUnit(unit)
                .durationMinutes(durationMinutes)
                .basePrice(new BigDecimal("100000"))
                .isActive(active)
                .build();
    }
}
