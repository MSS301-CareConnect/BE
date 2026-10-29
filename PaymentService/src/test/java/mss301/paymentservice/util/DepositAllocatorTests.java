package mss301.paymentservice.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DepositAllocatorTests {

    static final Instant START = Instant.parse("2026-11-10T01:00:00Z");
    static final BigDecimal THIRTY = BigDecimal.valueOf(30);

    @Test
    void oddPricesRoundEachOccurrenceAndPutRemainderOnLast() {
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(
                line(1, 0, "100001"), line(2, 1, "200000"), line(3, 2, "150000")), THIRTY);

        assertThat(result.totalPrice()).isEqualByComparingTo("450001");
        assertThat(result.totalDeposit()).isEqualByComparingTo("135000");
        assertThat(deposits(result)).containsExactly("30000", "60000", "45000");
    }

    @Test
    void halfDongRoundsUp() {
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(line(1, 0, "5"), line(2, 1, "5")), THIRTY);

        // 30 % × 10 = 3; buổi 1 = round(1,5) = 2, buổi cuối nhận 1.
        assertThat(result.totalDeposit()).isEqualByComparingTo("3");
        assertThat(deposits(result)).containsExactly("2", "1");
    }

    @Test
    void sixtyOccurrencesSumExactlyToTotal() {
        List<DepositAllocator.Line> lines = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            lines.add(line(i + 1, i, String.valueOf(333_333 + i)));
        }
        DepositAllocator.Result result = DepositAllocator.allocate(lines, THIRTY);

        BigDecimal sum = BigDecimal.ZERO;
        for (DepositAllocator.Allocation allocation : result.allocations()) {
            assertThat(allocation.depositAmount().signum()).isGreaterThanOrEqualTo(0);
            sum = sum.add(allocation.depositAmount());
        }
        assertThat(result.allocations()).hasSize(60);
        assertThat(sum).isEqualByComparingTo(result.totalDeposit());
        assertThat(result.totalDeposit()).isEqualByComparingTo(VndMoney.percentOf(THIRTY, result.totalPrice()));
    }

    @Test
    void zeroPercentMeansNoDeposit() {
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(line(1, 0, "100000"), line(2, 1, "90000")),
                BigDecimal.ZERO);

        assertThat(result.totalDeposit()).isEqualByComparingTo("0");
        assertThat(deposits(result)).containsExactly("0", "0");
    }

    @Test
    void hundredPercentDepositsFullPrice() {
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(line(1, 0, "100000"), line(2, 1, "90001")),
                BigDecimal.valueOf(100));

        assertThat(result.totalDeposit()).isEqualByComparingTo("190001");
        assertThat(deposits(result)).containsExactly("100000", "90001");
    }

    @Test
    void singleOccurrenceTakesWholeDeposit() {
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(line(1, 0, "1000000")), THIRTY);

        assertThat(deposits(result)).containsExactly("300000");
    }

    @Test
    void sortsByStartThenOccurrenceNumber() {
        UUID late = UUID.randomUUID();
        UUID earlySecond = UUID.randomUUID();
        UUID earlyFirst = UUID.randomUUID();
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(
                new DepositAllocator.Line(late, 1, START.plus(Duration.ofDays(2)), new BigDecimal("100000")),
                new DepositAllocator.Line(earlySecond, 3, START, new BigDecimal("100000")),
                new DepositAllocator.Line(earlyFirst, 2, START, new BigDecimal("100000"))), THIRTY);

        assertThat(result.allocations()).extracting(DepositAllocator.Allocation::occurrenceId)
                .containsExactly(earlyFirst, earlySecond, late);
    }

    @Test
    void neverAllocatesNegativeDepositWhenRoundingOvershoots() {
        // 3 buổi 5đ (round(1,5) = 2 mỗi buổi) + buổi cuối 1đ: tổng = round(4,8) = 5 < 2+2+2 → buổi cuối phải gánh −1.
        DepositAllocator.Result result = DepositAllocator.allocate(List.of(line(1, 0, "5"), line(2, 1, "5"),
                line(3, 2, "5"), line(4, 3, "1")), THIRTY);

        BigDecimal sum = BigDecimal.ZERO;
        for (DepositAllocator.Allocation allocation : result.allocations()) {
            assertThat(allocation.depositAmount().signum()).isGreaterThanOrEqualTo(0);
            sum = sum.add(allocation.depositAmount());
        }
        assertThat(sum).isEqualByComparingTo(result.totalDeposit());
    }

    private DepositAllocator.Line line(int occurrenceNo, int dayOffset, String price) {
        return new DepositAllocator.Line(UUID.randomUUID(), occurrenceNo, START.plus(Duration.ofDays(dayOffset)),
                new BigDecimal(price));
    }

    private List<String> deposits(DepositAllocator.Result result) {
        return result.allocations().stream()
                .map(allocation -> allocation.depositAmount().stripTrailingZeros().toPlainString())
                .toList();
    }
}
