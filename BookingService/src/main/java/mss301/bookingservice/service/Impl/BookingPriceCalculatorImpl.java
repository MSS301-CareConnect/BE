package mss301.bookingservice.service.Impl;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.enums.UnitType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.BookingPriceCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class BookingPriceCalculatorImpl implements BookingPriceCalculator {

    static BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    // Số giờ lưu 4 chữ số thập phân để hiển thị; tiền tính từ số phút chính xác rồi mới làm tròn đến đồng.
    static int UNIT_SCALE = 4;
    static int AMOUNT_SCALE = 10;

    @Override
    public BookingQuote calculate(List<ServiceQuote> services, List<OccurrenceSlot> slots) {
        for (ServiceQuote service : services) {
            service.setOccurrenceCount(slots.size());
            service.setQuantity(BigDecimal.ZERO);
            service.setTotalPrice(BigDecimal.ZERO);
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int index = 0; index < slots.size(); index++) {
            OccurrenceSlot slot = slots.get(index);
            BigDecimal priceAmount = BigDecimal.ZERO;
            for (ServiceQuote service : services) {
                BigDecimal linePrice = calculateLinePrice(service, slot.getDurationMinutes());
                BigDecimal units = calculateUnits(service, slot.getDurationMinutes());
                // SHIFT / RECURRING: mọi buổi cùng số đơn vị. CUSTOM mỗi buổi một khung giờ → số đơn vị khác nhau thì để
                // null (quantity vẫn là tổng đúng).
                if (index == 0) {
                    service.setUnitsPerOccurrence(units);
                } else if (service.getUnitsPerOccurrence() != null && service.getUnitsPerOccurrence().compareTo(units) != 0) {
                    service.setUnitsPerOccurrence(null);
                }
                service.setQuantity(service.getQuantity().add(units));
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

    // Spec §7.2 (Q11): HOUR = số phút / 60, không làm tròn lên giờ (90 phút = 1,5 giờ); DAY = 1; SHIFT = 1.
    private BigDecimal calculateUnits(ServiceQuote service, int occurrenceMinutes) {
        return switch (service.getUnitType()) {
            case HOUR -> BigDecimal.valueOf(occurrenceMinutes).divide(MINUTES_PER_HOUR, UNIT_SCALE, RoundingMode.HALF_UP);
            case DAY -> BigDecimal.ONE.setScale(UNIT_SCALE, RoundingMode.UNNECESSARY);
            case SHIFT -> {
                if (service.getPlanDurationMinutes() != null && occurrenceMinutes > service.getPlanDurationMinutes()) {
                    throw new AppException(ErrorCode.PRICE_PLAN_UNIT_MISMATCH);
                }
                yield BigDecimal.ONE.setScale(UNIT_SCALE, RoundingMode.UNNECESSARY);
            }
        };
    }

    // HOUR: đơn giá × phút / 60 chính xác (chưa làm tròn); giá buổi làm tròn đến đồng ở calculate (spec §7.2).
    private BigDecimal calculateLinePrice(ServiceQuote service, int occurrenceMinutes) {
        if (service.getUnitType() != UnitType.HOUR) {
            return service.getUnitPrice();
        }
        return service.getUnitPrice().multiply(BigDecimal.valueOf(occurrenceMinutes))
                .divide(MINUTES_PER_HOUR, AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    // VND làm tròn đến đồng (§3.3); cột numeric(14,2) giữ scale 2.
    private BigDecimal roundToDong(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.UNNECESSARY);
    }
}
