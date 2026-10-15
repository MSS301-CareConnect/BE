package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.RefundQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.CancelledBy;

import java.time.Instant;

/**
 * Chính sách hoàn tiền khi booking kết thúc trước hạn. Trung tâm hủy → hoàn toàn bộ (đã chốt). Khách hủy: % theo mốc
 * ngày so với buổi đầu — CHƯA CHỐT (AGENTS.md) → TODO, trả "chưa tính được". Chưa có Payment nên chỉ tính, không chi tiền.
 */
public interface RefundPolicy {
    RefundQuote quote(Booking booking, CancelledBy cancelledBy, Instant cancelledAt);
}
