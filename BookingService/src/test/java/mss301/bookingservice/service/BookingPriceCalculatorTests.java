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

    // AC BK-01: HOUR 100.000 + DAY 300.000, 3 buổi × 150 phút → HOUR 3 đơn vị / buổi, giá buổi 600.000, tổng 1.800.000.
    @Test
    void pricesEachOccurrenceByUnits() {
        ServiceQuote hourly = serviceQuote(UnitType.HOUR, 60, "100000");
        ServiceQuote daily = serviceQuote(UnitType.DAY, 480, "300000");
        BookingQuote quote = bookingPriceCalculator.calculate(List.of(hourly, daily), slots(3, 150));

        assertThat(quote.getOccurrences()).extracting(OccurrenceSlot::getPriceAmount)
                .allSatisfy(price -> assertThat(price).isEqualByComparingTo("600000"));
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("1800000");
        assertThat(quote.getSubtotal()).isEqualByComparingTo("1800000");
        assertThat(hourly.getUnitsPerOccurrence()).isEqualTo(3);
        assertThat(hourly.getQuantity()).isEqualTo(9);
        assertThat(hourly.getTotalPrice()).isEqualByComparingTo("900000");
        assertThat(daily.getUnitsPerOccurrence()).isEqualTo(1);
        assertThat(daily.getOccurrenceCount()).isEqualTo(3);
        assertThat(daily.getTotalPrice()).isEqualByComparingTo("900000");
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
