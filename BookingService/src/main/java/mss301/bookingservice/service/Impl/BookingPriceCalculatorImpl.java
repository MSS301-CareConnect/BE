package mss301.bookingservice.service.Impl;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.BookingPriceCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BookingPriceCalculatorImpl implements BookingPriceCalculator {

    static int MINUTES_PER_HOUR = 60;

    @Override
    public BookingQuote calculate(List<ServiceQuote> services, List<OccurrenceSlot> slots) {
        for (ServiceQuote service : services) {
            service.setOccurrenceCount(slots.size());
            service.setQuantity(0);
            service.setTotalPrice(BigDecimal.ZERO);
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int index = 0; index < slots.size(); index++) {
            OccurrenceSlot slot = slots.get(index);
            BigDecimal priceAmount = BigDecimal.ZERO;
            for (ServiceQuote service : services) {
                int units = calculateUnits(service, slot.getDurationMinutes());
                BigDecimal linePrice = service.getUnitPrice().multiply(BigDecimal.valueOf(units));
                // SHIFT / RECURRING: mọi buổi cùng số đơn vị. CUSTOM mỗi buổi một khung giờ → số đơn vị khác nhau thì để
                // null (quantity vẫn là tổng đúng).
                if (index == 0) {
                    service.setUnitsPerOccurrence(units);
                } else if (service.getUnitsPerOccurrence() != null && service.getUnitsPerOccurrence() != units) {
                    service.setUnitsPerOccurrence(null);
                }
                service.setQuantity(service.getQuantity() + units);
                service.setTotalPrice(service.getTotalPrice().add(linePrice));
                priceAmount = priceAmount.add(linePrice);
            }
            slot.setPriceAmount(roundToDong(priceAmount));
            totalAmount = totalAmount.add(slot.getPriceAmount());
        }
        for (ServiceQuote service : services) {
            service.setTotalPrice(roundToDong(service.getTotalPrice()));
        }
        return BookingQuote.builder()
                .items(services)
                .occurrences(slots)
                .subtotal(totalAmount)
                .totalAmount(totalAmount)
                .build();
    }

    private int calculateUnits(ServiceQuote service, int occurrenceMinutes) {
        return switch (service.getUnitType()) {
            case HOUR -> (occurrenceMinutes + MINUTES_PER_HOUR - 1) / MINUTES_PER_HOUR;
            case DAY -> 1;
            case SHIFT -> {
                if (service.getPlanDurationMinutes() != null && occurrenceMinutes > service.getPlanDurationMinutes()) {
                    throw new AppException(ErrorCode.PRICE_PLAN_UNIT_MISMATCH);
                }
                yield 1;
            }
        };
    }

    // VND làm tròn đến đồng (§3.3); cột numeric(14,2) giữ scale 2.
    private BigDecimal roundToDong(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }
}
