package mss301.bookingservice.service.Impl;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.QuoteUnitGroup;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.enums.UnitType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.BookingPriceCalculator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingPriceCalculatorImpl implements BookingPriceCalculator {

    static BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    // Số giờ lưu 4 chữ số thập phân để hiển thị; tiền tính từ số phút chính xác rồi mới làm tròn đến đồng.
    static int UNIT_SCALE = 4;
    static int AMOUNT_SCALE = 10;

    // Tính giá từng buổi theo đơn giá và số đơn vị, làm tròn đồng, cộng tổng.
    @Override
    public BookingQuote calculate(List<ServiceQuote> services, List<OccurrenceSlot> slots) {
        checkSlotServices(services, slots);
        for (ServiceQuote service : services) {
            service.setOccurrenceCount(0);
            service.setQuantity(BigDecimal.ZERO);
            service.setTotalPrice(BigDecimal.ZERO);
            service.setUnitsPerOccurrence(null);
        }
        // Mỗi dịch vụ: số đơn vị mỗi buổi → [số buổi, tiền chưa làm tròn], để báo giá ghi riêng buổi dài hơn.
        List<Map<BigDecimal, BigDecimal[]>> groups = new ArrayList<>();
        for (int serviceIndex = 0; serviceIndex < services.size(); serviceIndex++) {
            groups.add(new TreeMap<>());
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OccurrenceSlot slot : slots) {
            BigDecimal priceAmount = BigDecimal.ZERO;
            for (int serviceIndex = 0; serviceIndex < services.size(); serviceIndex++) {
                ServiceQuote service = services.get(serviceIndex);
                // Buổi gắn một dịch vụ (2026-11-05) chỉ tính dịch vụ đó; buổi không gắn tính mọi dịch vụ (như trước).
                if (slot.getCareServiceId() != null && !slot.getCareServiceId().equals(service.getCareServiceId())) {
                    continue;
                }
                if (slot.getCareServiceId() != null) {
                    checkPlanDuration(service, slot.getDurationMinutes());
                }
                BigDecimal linePrice = calculateLinePrice(service, slot.getDurationMinutes());
                BigDecimal units = calculateUnits(service, slot.getDurationMinutes());
                BigDecimal[] group = groups.get(serviceIndex).computeIfAbsent(units,
                        key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                group[0] = group[0].add(BigDecimal.ONE);
                group[1] = group[1].add(linePrice);
                // SHIFT / RECURRING: mọi buổi cùng số đơn vị. CUSTOM mỗi buổi một khung giờ → số đơn vị khác nhau thì để
                // null (quantity vẫn là tổng đúng).
                if (service.getOccurrenceCount() == 0) {
                    service.setUnitsPerOccurrence(units);
                } else if (service.getUnitsPerOccurrence() != null && service.getUnitsPerOccurrence().compareTo(units) != 0) {
                    service.setUnitsPerOccurrence(null);
                }
                service.setOccurrenceCount(service.getOccurrenceCount() + 1);
                service.setQuantity(service.getQuantity().add(units));
                service.setTotalPrice(service.getTotalPrice().add(linePrice));
                priceAmount = priceAmount.add(linePrice);
            }
            slot.setPriceAmount(roundToDong(priceAmount));
            totalAmount = totalAmount.add(slot.getPriceAmount());
        }
        for (int serviceIndex = 0; serviceIndex < services.size(); serviceIndex++) {
            ServiceQuote service = services.get(serviceIndex);
            service.setTotalPrice(roundToDong(service.getTotalPrice()));
            List<QuoteUnitGroup> unitGroups = new ArrayList<>();
            for (Map.Entry<BigDecimal, BigDecimal[]> entry : groups.get(serviceIndex).entrySet()) {
                unitGroups.add(QuoteUnitGroup.builder()
                        .unitsPerOccurrence(entry.getKey())
                        .occurrenceCount(entry.getValue()[0].intValue())
                        .amount(roundToDong(entry.getValue()[1]))
                        .build());
            }
            service.setUnitGroups(unitGroups);
        }
        return BookingQuote.builder()
                .items(services)
                .occurrences(slots)
                .subtotal(totalAmount)
                .totalAmount(totalAmount)
                .build();
    }

    // Đa dịch vụ theo khung giờ (2026-11-05): hoặc mọi buổi gắn dịch vụ, hoặc không buổi nào; dịch vụ của buổi phải nằm
    // trong booking và mỗi dịch vụ đã chọn có ít nhất một buổi.
    private void checkSlotServices(List<ServiceQuote> services, List<OccurrenceSlot> slots) {
        long taggedCount = slots.stream()
                .filter(slot -> slot.getCareServiceId() != null)
                .count();
        if (taggedCount == 0) {
            return;
        }
        if (taggedCount != slots.size()) {
            throw new AppException(ErrorCode.SLOT_SERVICE_MIXED);
        }
        Set<UUID> selectedIds = services.stream()
                .map(ServiceQuote::getCareServiceId)
                .collect(Collectors.toSet());
        Set<UUID> slotServiceIds = new HashSet<>();
        for (OccurrenceSlot slot : slots) {
            if (!selectedIds.contains(slot.getCareServiceId())) {
                throw new AppException(ErrorCode.SLOT_SERVICE_NOT_SELECTED);
            }
            slotServiceIds.add(slot.getCareServiceId());
        }
        if (!slotServiceIds.containsAll(selectedIds)) {
            throw new AppException(ErrorCode.SLOT_SERVICE_WITHOUT_SLOT);
        }
    }

    // Buổi gắn một dịch vụ: gói DAY / SHIFT có độ dài cố định → buổi phải đúng bằng; gói HOUR → ít nhất bằng thời lượng gói.
    private void checkPlanDuration(ServiceQuote service, int occurrenceMinutes) {
        Integer planMinutes = service.getPlanDurationMinutes();
        if (planMinutes == null) {
            return;
        }
        boolean mismatch = service.getUnitType() == UnitType.HOUR
                ? occurrenceMinutes < planMinutes
                : occurrenceMinutes != planMinutes;
        if (mismatch) {
            throw new AppException(ErrorCode.PRICE_PLAN_UNIT_MISMATCH);
        }
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
