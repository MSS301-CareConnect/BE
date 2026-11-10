package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.CenterOfferingRequest;
import mss301.carecatalogservice.dto.request.PricePlanBatchUpdateRequest;
import mss301.carecatalogservice.dto.request.PricePlanDraftRequest;
import mss301.carecatalogservice.dto.request.PricePlanRequest;
import mss301.carecatalogservice.dto.response.APIResponse;
import mss301.carecatalogservice.dto.response.CenterOfferingResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        pricePlanService = new PricePlanServiceImpl(pricePlanRepository, centerOfferingRepository, new PricePlanMapperImpl());
        centerOfferingService = new CenterOfferingServiceImpl(centerOfferingRepository, catalogItemRepository, pricePlanRepository,
                new CenterOfferingMapperImpl(new CatalogItemMapperImpl(), new PricePlanMapperImpl()), catalogQuoteService,
                pricePlanService);
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
    void discountDefaultsToZeroAndFinalPriceIsRoundedToDong() {
        CenterOffering offering = activeOffering();
        when(centerOfferingRepository.findByIdAndDeletedAtIsNull(offeringId)).thenReturn(Optional.of(offering));
        when(pricePlanRepository.save(any(PricePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PricePlanResponse plain = pricePlanService.create(centerId, planRequest(PricingUnit.HOUR, 60, true)).getResult();
        assertThat(plain.getDiscountPercent()).isZero();
        assertThat(plain.getFinalPrice()).isEqualByComparingTo("100000");

        PricePlanRequest discounted = planRequest(PricingUnit.SHIFT, 240, true);
        discounted.setBasePrice(new BigDecimal("99999"));
        discounted.setDiscountPercent(15);
        PricePlanResponse response = pricePlanService.create(centerId, discounted).getResult();
        assertThat(response.getBasePrice()).isEqualByComparingTo("99999");
        // 99 999 × 85 % = 84 999,15 → 84 999 đồng
        assertThat(response.getFinalPrice()).isEqualByComparingTo("84999");
    }

    @Test
    void updateWithoutDiscountKeepsStoredDiscount() {
        CenterOffering offering = activeOffering();
        PricePlan plan = PricePlan.builder().centerOffering(offering).planName("Ca").pricingUnit(PricingUnit.SHIFT)
                .durationMinutes(240).basePrice(new BigDecimal("200000")).discountPercent(20).build();
        UUID planId = UUID.randomUUID();
        plan.setId(planId);
        when(pricePlanRepository.findByIdAndDeletedAtIsNull(planId)).thenReturn(Optional.of(plan));
        when(pricePlanRepository.saveAndFlush(any(PricePlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PricePlanResponse response = pricePlanService.update(centerId, planId, planRequest(PricingUnit.SHIFT, 240, null))
                .getResult();
        assertThat(response.getDiscountPercent()).isEqualTo(20);
        assertThat(response.getFinalPrice()).isEqualByComparingTo("80000");
    }

    @Test
    void batchUpdateAppliesEveryPlanOrRejectsTheWholeBatch() {
        CenterOffering offering = activeOffering();
        PricePlan first = PricePlan.builder().centerOffering(offering).planName("A").pricingUnit(PricingUnit.HOUR)
                .durationMinutes(60).basePrice(BigDecimal.TEN).build();
        first.setId(UUID.randomUUID());
        first.setVersion(3L);
        PricePlan second = PricePlan.builder().centerOffering(offering).planName("B").pricingUnit(PricingUnit.DAY)
                .durationMinutes(480).basePrice(BigDecimal.TEN).build();
        second.setId(UUID.randomUUID());
        second.setVersion(1L);
        when(pricePlanRepository.findByIdAndDeletedAtIsNull(first.getId())).thenReturn(Optional.of(first));
        when(pricePlanRepository.findByIdAndDeletedAtIsNull(second.getId())).thenReturn(Optional.of(second));
        when(pricePlanRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        PricePlanRequest firstRequest = planRequest(PricingUnit.HOUR, 60, true);
        firstRequest.setVersion(3L);
        firstRequest.setDiscountPercent(10);
        PricePlanRequest secondRequest = planRequest(PricingUnit.DAY, 480, false);
        secondRequest.setVersion(1L);
        List<PricePlanResponse> responses = pricePlanService.updateBatch(centerId, PricePlanBatchUpdateRequest.builder()
                .items(List.of(batchItem(first.getId(), firstRequest), batchItem(second.getId(), secondRequest)))
                .build()).getResult();
        assertThat(responses).extracting(PricePlanResponse::getFinalPrice)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("90000"), new BigDecimal("100000"));
        assertThat(second.getIsActive()).isFalse();

        // Version cũ ở một gói → cả lô bị từ chối, không gọi lưu.
        secondRequest.setVersion(0L);
        assertThatThrownBy(() -> pricePlanService.updateBatch(centerId, PricePlanBatchUpdateRequest.builder()
                .items(List.of(batchItem(first.getId(), firstRequest), batchItem(second.getId(), secondRequest)))
                .build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONCURRENT_UPDATE);
        assertThatThrownBy(() -> pricePlanService.updateBatch(centerId, PricePlanBatchUpdateRequest.builder()
                .items(List.of(batchItem(first.getId(), firstRequest), batchItem(first.getId(), firstRequest)))
                .build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICE_PLAN_BATCH_INVALID);
        verify(pricePlanRepository).saveAllAndFlush(anyList());
    }

    @Test
    void offeringCanBeCreatedTogetherWithPricePlans() {
        CatalogItem item = activeOffering().getCatalogItem();
        when(catalogItemRepository.findByIdAndDeletedAtIsNull(catalogItemId)).thenReturn(Optional.of(item));
        when(centerOfferingRepository.save(any(CenterOffering.class))).thenAnswer(invocation -> {
            CenterOffering saved = invocation.getArgument(0);
            saved.setId(offeringId);
            return saved;
        });
        when(pricePlanRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        PricePlanDraftRequest hourly = PricePlanDraftRequest.builder().planName("Theo giờ").pricingUnit(PricingUnit.HOUR)
                .durationMinutes(60).basePrice(new BigDecimal("120000")).discountPercent(25).build();
        PricePlanDraftRequest shift = PricePlanDraftRequest.builder().planName("Ca 4 giờ").pricingUnit(PricingUnit.SHIFT)
                .durationMinutes(240).basePrice(new BigDecimal("400000")).isActive(false).build();

        CenterOfferingResponse response = centerOfferingService.create(centerId, CenterOfferingRequest.builder()
                .catalogItemId(catalogItemId).status(ServiceStatus.ACTIVE).pricePlans(List.of(hourly, shift)).build())
                .getResult();
        assertThat(response.getPricePlans()).hasSize(2);
        assertThat(response.getPricePlans().get(0).getFinalPrice()).isEqualByComparingTo("90000");
        assertThat(response.getPricePlans().get(0).getCenterOfferingId()).isEqualTo(offeringId);
        assertThat(response.getPricePlans().get(1).getIsActive()).isFalse();
        assertThat(response.getPricePlans().get(1).getDiscountPercent()).isZero();

        // Gói HOUR sai thời lượng → lỗi trước khi lưu gói (transaction rollback cả dịch vụ ở runtime).
        PricePlanDraftRequest badHourly = PricePlanDraftRequest.builder().planName("Sai").pricingUnit(PricingUnit.HOUR)
                .durationMinutes(90).basePrice(BigDecimal.ONE).build();
        assertThatThrownBy(() -> centerOfferingService.create(centerId, CenterOfferingRequest.builder()
                .catalogItemId(catalogItemId).status(ServiceStatus.ACTIVE).pricePlans(List.of(badHourly)).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_DURATION);
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

    PricePlanBatchUpdateRequest.Item batchItem(UUID id, PricePlanRequest plan) {
        return PricePlanBatchUpdateRequest.Item.builder().id(id).plan(plan).build();
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
