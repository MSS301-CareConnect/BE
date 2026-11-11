package mss301.bookingservice.dto.response;

import java.util.Set;
import java.util.UUID;

// Các buổi có ca tự đóng đang chờ trung tâm xác nhận (payment-context, 2026-11-11). Kiểu riêng vì MapStruct phân biệt
// tham số @Context theo kiểu — đã có một Set<UUID> khác (buổi có ca).
public record PaymentHeldOccurrences(Set<UUID> occurrenceIds) {
    public boolean contains(UUID occurrenceId) {
        return occurrenceIds.contains(occurrenceId);
    }
}
