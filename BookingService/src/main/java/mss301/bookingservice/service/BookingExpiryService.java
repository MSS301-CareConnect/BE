package mss301.bookingservice.service;

import java.util.List;
import java.util.UUID;

// Chuyển EXPIRED: PENDING quá hạn duyệt, AWAITING_DEPOSIT quá hạn cọc (giải phóng caregiver / người bệnh).
public interface BookingExpiryService {
    List<UUID> findExpirableIds();

    // Transaction ngắn riêng cho từng booking: khóa dòng, kiểm lại hạn rồi mới chuyển trạng thái.
    void expire(UUID id);
}
