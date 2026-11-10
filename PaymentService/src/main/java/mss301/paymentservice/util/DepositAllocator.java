package mss301.paymentservice.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Phân bổ cọc theo buổi (PAY-04, GAP_ANALYSIS D2): sắp theo giờ bắt đầu rồi số thứ tự buổi; tổng cọc =
 * round(% × Σ giá); mỗi buổi trừ buổi cuối = round(% × giá_i); buổi cuối = tổng − Σ các buổi khác (phần lệch do làm tròn
 * dồn vào buổi cuối). Hàm thuần — không đọc DB, không phụ thuộc Spring.
 */
public final class DepositAllocator {

    private DepositAllocator() {
    }

    public static Result allocate(List<Line> lines, BigDecimal depositPercent) {
        List<Line> sorted = new ArrayList<>(lines);
        sorted.sort(Comparator.comparing(Line::startAt)
                .thenComparing(Line::occurrenceNo, Comparator.nullsLast(Comparator.naturalOrder())));
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Line line : sorted) {
            totalPrice = totalPrice.add(line.priceAmount());
        }
        BigDecimal totalDeposit = VndMoney.percentOf(depositPercent, totalPrice);
        BigDecimal[] deposits = new BigDecimal[sorted.size()];
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < sorted.size() - 1; i++) {
            deposits[i] = VndMoney.percentOf(depositPercent, sorted.get(i).priceAmount());
            allocated = allocated.add(deposits[i]);
        }
        if (!sorted.isEmpty()) {
            deposits[sorted.size() - 1] = totalDeposit.subtract(allocated);
            rebalanceNegativeLast(deposits);
        }
        List<Allocation> allocations = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Line line = sorted.get(i);
            allocations.add(new Allocation(line.occurrenceId(), line.startAt(), line.priceAmount(), deposits[i]));
        }
        return new Result(allocations, totalPrice, totalDeposit);
    }

    // Giá lẻ bất thường (vd nhiều buổi 5đ) làm Σ làm tròn từng buổi vượt tổng → buổi cuối âm. Giữ nguyên tổng, lấy bớt
    // từ các buổi trước (từ cuối lên) để không buổi nào có cọc âm (CHECK deposit_amount >= 0).
    private static void rebalanceNegativeLast(BigDecimal[] deposits) {
        int last = deposits.length - 1;
        for (int i = last - 1; i >= 0 && deposits[last].signum() < 0; i--) {
            BigDecimal take = deposits[i].min(deposits[last].negate());
            deposits[i] = deposits[i].subtract(take);
            deposits[last] = deposits[last].add(take);
        }
    }

    public record Line(UUID occurrenceId, Integer occurrenceNo, Instant startAt, BigDecimal priceAmount) {
    }

    public record Allocation(UUID occurrenceId, Instant startAt, BigDecimal priceAmount, BigDecimal depositAmount) {
    }

    public record Result(List<Allocation> allocations, BigDecimal totalPrice, BigDecimal totalDeposit) {
    }
}
