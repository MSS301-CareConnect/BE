package mss301.bookingservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * previewToken của BK-03: hạn + HMAC(bookingId, các buổi, totalRefund, hạn) bằng khóa nội bộ — không lưu DB. Lệnh hủy
 * tính lại số tiền rồi so token để chắc khách đồng ý đúng số đang áp dụng (vd chưa qua mốc 24h).
 */
public interface CancellationTokenService {
    String sign(UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund, Instant expiresAt);

    // Hết hạn / sai định dạng / không khớp kết quả tính lại → 409 CANCELLATION_PREVIEW_EXPIRED.
    void verify(String previewToken, UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund, Instant now);
}
