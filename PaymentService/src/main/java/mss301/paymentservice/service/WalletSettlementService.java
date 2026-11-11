package mss301.paymentservice.service;

import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.BookingSettlementResponse;
import mss301.paymentservice.enums.RoleCode;

import java.util.List;
import java.util.Set;
import java.util.UUID;

// Worker cộng tiền buổi hoàn tất vào ví trung tâm / nền tảng (S1: hỏi payment-context của Booking, không cần sự kiện).
public interface WalletSettlementService {

    List<UUID> findDueBookingIds();

    void process(UUID bookingId);

    // 2026-11-11: quyết toán một booking — ADMIN mọi booking, CENTER_MANAGER booking của trung tâm trong token.
    APIResponse<BookingSettlementResponse> getBookingSettlement(Set<RoleCode> roles, UUID claimCenterId, UUID bookingId);
}
