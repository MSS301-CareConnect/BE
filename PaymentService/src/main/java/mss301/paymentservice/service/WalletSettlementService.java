package mss301.paymentservice.service;

import java.util.List;
import java.util.UUID;

// Worker cộng tiền buổi hoàn tất vào ví trung tâm / nền tảng (S1: hỏi payment-context của Booking, không cần sự kiện).
public interface WalletSettlementService {

    List<UUID> findDueBookingIds();

    void process(UUID bookingId);
}
