package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.enums.UnitType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.Impl.BookingPriceCalculatorImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPriceCalculatorTests {

    BookingPriceCalculator bookingPriceCalculator = new BookingPriceCalculatorImpl();

    // Spec §7.2 (Q11): HOUR 100.000 + DAY 300.000, 3 buổi × 150 phút → HOUR 2,5 giờ / buổi, giá buổi 550.000,
    // tổng 1.650.000 (trước đây làm tròn lên 3 giờ).
    @Test
    void pricesEachOccurrenceByUnits() {
        ServiceQuote hourly = serviceQuote(UnitType.HOUR, 60, "100000");
        ServiceQuote daily = serviceQuote(UnitType.DAY, 480, "300000");
        BookingQuote quote = bookingPriceCalculator.calculate(List.of(hourly, daily), slots(3, 150));

        assertThat(quote.getOccurrences()).extracting(OccurrenceSlot::getPriceAmount)
                .allSatisfy(price -> assertThat(price).isEqualByComparingTo("550000"));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("1650000");
        assertThat(quote.getSubtotal()).isEqualByComparingTo("1650000");
        assertThat(hourly.getUnitsPerOccurrence()).isEqualByComparingTo("2.5");
        assertThat(hourly.getQuantity()).isEqualByComparingTo("7.5");
        assertThat(hourly.getTotalPrice()).isEqualByComparingTo("750000");
        assertThat(daily.getUnitsPerOccurrence()).isEqualByComparingTo("1");
        assertThat(daily.getOccurrenceCount()).isEqualTo(3);
        assertThat(daily.getTotalPrice()).isEqualByComparingTo("900000");
    }

    // Q11: 90 phút = 1,5 giờ; giờ lẻ không chia hết vẫn tính tiền theo phút chính xác rồi làm tròn đến đồng.
    @Test
    void chargesFractionalHours() {
        ServiceQuote hourly = serviceQuote(UnitType.HOUR, 60, "100000");
        BookingQuote quote = bookingPriceCalculator.calculate(List.of(hourly), slots(1, 90));
        assertThat(hourly.getUnitsPerOccurrence()).isEqualByComparingTo("1.5");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("150000");

        ServiceQuote odd = serviceQuote(UnitType.HOUR, 60, "100000");
        BookingQuote oddQuote = bookingPriceCalculator.calculate(List.of(odd), slots(1, 70));
        assertThat(odd.getUnitsPerOccurrence()).isEqualByComparingTo("1.1667");
        assertThat(oddQuote.getTotalAmount()).isEqualByComparingTo("116667");
    }

    @Test
    void shiftPlanRejectsLongerOccurrence() {
        ServiceQuote shift = serviceQuote(UnitType.SHIFT, 240, "500000");
        assertThatThrownBy(() -> bookingPriceCalculator.calculate(List.of(shift), slots(1, 300)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICE_PLAN_UNIT_MISMATCH);
        BookingQuote quote = bookingPriceCalculator.calculate(List.of(serviceQuote(UnitType.SHIFT, 240, "500000")),
                slots(2, 240));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("1000000");
    }

    @Test
    void roundsToWholeDong() {
        ServiceQuote odd = serviceQuote(UnitType.HOUR, 60, "33333.50");
        BookingQuote quote = bookingPriceCalculator.calculate(List.of(odd), slots(1, 60));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("33334");
    }

    ServiceQuote serviceQuote(UnitType unitType, int planMinutes, String unitPrice) {
        return ServiceQuote.builder()
                .careServiceId(UUID.randomUUID())
                .unitType(unitType)
                .planDurationMinutes(planMinutes)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }

    List<OccurrenceSlot> slots(int count, int minutes) {
        List<OccurrenceSlot> slots = new ArrayList<>();
        Instant start = Instant.parse("2026-11-02T01:00:00Z");
        for (int i = 0; i < count; i++) {
            Instant slotStart = start.plusSeconds(86400L * i);
            slots.add(OccurrenceSlot.builder()
                    .occurrenceNo(i + 1)
                    .startAt(slotStart)
                    .endAt(slotStart.plusSeconds(60L * minutes))
                    .durationMinutes(minutes)
                    .build());
        }
        return slots;
    }
}
