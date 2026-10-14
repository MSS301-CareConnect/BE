package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
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
import mss301.bookingservice.service.CareServiceCatalogPort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter báo giá dịch vụ với CareCatalogService.
 * Khi BOOKING_CATALOG_INTEGRATION_ENABLED=false: giữ id khách chọn, tên / đơn vị / giá / tổng tiền NULL.
 * Khi BOOKING_CATALOG_INTEGRATION_ENABLED=true: gọi Feign tới CareCatalogService, tính số lượng, đơn giá,
 * thành tiền, tiền cọc và tổng tiền.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CareServiceCatalogPortImpl implements CareServiceCatalogPort {

    BookingDetailMapper bookingDetailMapper;
    BookingProperties bookingProperties;
    CareCatalogClient careCatalogClient;

    @Override
    public BookingQuote quote(UUID centerId, List<BookingServiceRequest> services, List<OccurrenceSlot> slots) {
        if (!bookingProperties.getIntegration().isCatalogEnabled()) {
            List<ServiceQuote> items = bookingDetailMapper.toUnpricedQuoteList(services);
            return BookingQuote.builder()
                    .items(items)
                    .build();
        }

        List<CareCatalogQuoteRequest.Item> items = services.stream()
                .map(s -> CareCatalogQuoteRequest.Item.builder()
                        .careServiceId(s.getCareServiceId())
                        .centerServiceId(s.getCenterServiceId())
                        .pricePlanId(s.getPricePlanId())
                        .build())
                .toList();

        CareCatalogQuoteRequest request = CareCatalogQuoteRequest.builder()
                .centerId(centerId)
                .items(items)
                .build();

        RemoteApiResponse<List<CareCatalogQuoteResponse>> response = careCatalogClient.quote(request);
        List<CareCatalogQuoteResponse> catalogQuotes = response == null ? null : response.getResult();
        if (catalogQuotes == null || catalogQuotes.isEmpty()) {
            throw new AppException(ErrorCode.CARE_CATALOG_UNAVAILABLE);
        }

        Map<UUID, CareCatalogQuoteResponse> quotesByCareServiceId = catalogQuotes.stream()
                .collect(Collectors.toMap(CareCatalogQuoteResponse::getCareServiceId, q -> q, (a, b) -> a));

        List<ServiceQuote> quotedServices = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        int totalSlotMinutes = slots.stream().mapToInt(OccurrenceSlot::getDurationMinutes).sum();
        int occurrenceCount = slots.size();

        for (BookingServiceRequest service : services) {
            CareCatalogQuoteResponse catalogItem = quotesByCareServiceId.get(service.getCareServiceId());
            if (catalogItem == null) {
                throw new AppException(ErrorCode.CENTER_SERVICE_NOT_SUPPORTED);
            }

            UnitType unitType = parseUnitType(catalogItem.getPricingUnit());
            int quantity = calculateQuantity(unitType, catalogItem.getDurationMinutes(), totalSlotMinutes, occurrenceCount);
            BigDecimal unitPrice = catalogItem.getBasePrice();
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

            quotedServices.add(ServiceQuote.builder()
                    .careServiceId(catalogItem.getCareServiceId())
                    .centerServiceId(catalogItem.getCenterServiceId())
                    .pricePlanId(catalogItem.getPricePlanId())
                    .serviceName(catalogItem.getServiceName())
                    .unitType(unitType)
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .totalPrice(totalPrice)
                    .build());

            subtotal = subtotal.add(totalPrice);
        }

        BigDecimal depositRate = bookingProperties.getDepositRate() != null
                ? bookingProperties.getDepositRate()
                : new BigDecimal("0.30");
        BigDecimal depositAmount = subtotal.multiply(depositRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal;

        log.info("Quote calculated for center {}: {} services, subtotal={}, deposit={}",
                centerId, quotedServices.size(), subtotal, depositAmount);

        return BookingQuote.builder()
                .items(quotedServices)
                .subtotal(subtotal)
                .depositAmount(depositAmount)
                .totalAmount(totalAmount)
                .build();
    }

    private UnitType parseUnitType(String unitString) {
        if (unitString == null) {
            return UnitType.HOUR;
        }
        try {
            return UnitType.valueOf(unitString.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown pricing unit '{}', defaulting to HOUR", unitString);
            return UnitType.HOUR;
        }
    }

    private int calculateQuantity(UnitType unitType, Integer durationMinutes, int totalSlotMinutes, int occurrenceCount) {
        return switch (unitType) {
            case SHIFT, DAY -> Math.max(1, occurrenceCount);
            case HOUR -> {
                int planDuration = (durationMinutes != null && durationMinutes > 0) ? durationMinutes : 60;
                yield Math.max(1, (int) Math.ceil((double) totalSlotMinutes / planDuration));
            }
        };
    }
}
