package mss301.bookingservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CancellationTokenService {
    // Ký token xem trước hủy (booking, buổi, tiền hoàn, hạn) để xác nhận hủy đúng số đã xem.
    String sign(UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund, Instant expiresAt);

    // Hết hạn / sai định dạng / không khớp kết quả tính lại → 409 CANCELLATION_PREVIEW_EXPIRED.
    void verify(String previewToken, UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund, Instant now);
}
