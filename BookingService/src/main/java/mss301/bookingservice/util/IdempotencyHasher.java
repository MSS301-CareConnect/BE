package mss301.bookingservice.util;

import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.BookingServiceRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * SHA-256 của nội dung yêu cầu đặt lịch đã chuẩn hóa (thứ tự dịch vụ / thứ trong tuần không ảnh hưởng). Cùng
 * Idempotency-Key + cùng hash → trả booking đã tạo; khác hash → IDEMPOTENCY_KEY_REUSED.
 */
public final class IdempotencyHasher {

    private static final String SEPARATOR = "|";
    private static final String ALGORITHM = "SHA-256";

    private IdempotencyHasher() {
    }

    public static String hash(BookingCreateRequest request) {
        List<String> parts = new ArrayList<>();
        parts.add(String.valueOf(request.getRecipientId()));
        parts.add(String.valueOf(request.getCenterId()));
        parts.add(String.valueOf(request.getCaregiverId()));
        parts.add(String.valueOf(request.getScheduleType()));
        parts.add(String.valueOf(request.getStartDate()));
        parts.add(String.valueOf(request.getEndDate()));
        parts.add(String.valueOf(request.getStartTime()));
        parts.add(String.valueOf(request.getEndTime()));
        parts.add(String.valueOf(sortedDays(request.getDaysOfWeek())));
        parts.add(String.valueOf(request.getCustomerNote()));
        parts.add(String.valueOf(sortedServices(request.getServices())));
        return sha256(String.join(SEPARATOR, parts));
    }

    private static List<Integer> sortedDays(List<Integer> daysOfWeek) {
        if (daysOfWeek == null) {
            return List.of();
        }
        return daysOfWeek.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
