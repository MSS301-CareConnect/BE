package mss301.bookingservice.util;

import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.request.TimeSlotRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class IdempotencyHasher {

    private static final String SEPARATOR = "|";
    private static final String ALGORITHM = "SHA-256";

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private IdempotencyHasher() {
    }

    // Băm nội dung request tạo booking để so khi gửi lại cùng idempotency key.
    public static String hash(BookingCreateRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add(String.valueOf(request.getRecipientId()));
        parts.add(String.valueOf(request.getCenterId()));
        parts.add(String.valueOf(request.getCaregiverId()));
        parts.add(String.valueOf(request.getBookingType()));
        parts.add(String.valueOf(request.getCaregiverScheduleId()));
        parts.add(String.valueOf(request.getFrequency()));
        parts.add(String.valueOf(request.getStartDate()));
        parts.add(String.valueOf(request.getEndDate()));
        parts.add(String.valueOf(request.getStartTime()));
        parts.add(String.valueOf(request.getEndTime()));
        parts.add(String.valueOf(sortedDays(request.getDaysOfWeek())));
        parts.add(String.valueOf(sortedSlots(request.getSlots())));
        parts.add(String.valueOf(request.getCustomerNote()));
        parts.add(String.valueOf(sortedServices(request.getServices())));
        return sha256(String.join(SEPARATOR, parts));
    }

    // Danh sách thứ trong tuần đã sắp xếp.
    private static List<Integer> sortedDays(List<Integer> daysOfWeek) {
        if (daysOfWeek == null) {
            return List.of();
        }
        return daysOfWeek.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    // CUSTOM: thứ tự buổi gửi lên không ảnh hưởng (generator sắp lại theo giờ bắt đầu).
    private static List<String> sortedSlots(List<TimeSlotRequest> slots) {
        if (slots == null) {
            return List.of();
        }
        return slots.stream()
                .filter(Objects::nonNull)
                .map(slot -> slot.getStartAt() + "/" + slot.getEndAt())
                .sorted()
                .toList();
    }

    // Danh sách dịch vụ đã chuẩn hóa và sắp xếp.
    private static List<String> sortedServices(List<BookingServiceRequest> services) {
        if (services == null) {
            return List.of();
        }
        return services.stream()
                .filter(Objects::nonNull)
                .map(service -> service.getCareServiceId() + ":" + service.getCenterServiceId() + ":" + service.getPricePlanId())
                .sorted()
                .toList();
    }

    // Băm SHA-256 một chuỗi sang hex.
    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
