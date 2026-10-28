package mss301.providerservice.service;

import mss301.providerservice.util.TimeWindow;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hỏi BookingService caregiver nào đã có buổi được giao trong các khung giờ (C5.1). Gọi mạng — chỉ dùng NGOÀI DB
 * transaction (rule #24). Booking lỗi → 503 BOOKING_SERVICE_UNAVAILABLE, không bao giờ coi là "rảnh".
 */
public interface BookingAvailabilityService {

    Set<UUID> findBusyCaregiverIds(Collection<UUID> caregiverIds, List<TimeWindow> slots);
}
