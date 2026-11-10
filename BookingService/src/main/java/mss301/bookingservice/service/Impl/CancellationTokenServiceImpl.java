package mss301.bookingservice.service.Impl;

import jakarta.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.service.CancellationTokenService;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CancellationTokenServiceImpl implements CancellationTokenService {

    static String HMAC_ALGORITHM = "HmacSHA256";
    static String TOKEN_SEPARATOR = ".";
    static String FIELD_SEPARATOR = "|";
    static int FALLBACK_KEY_BYTES = 32;
    // Chưa cấu hình khóa → khóa ngẫu nhiên theo tiến trình: token chỉ hợp lệ trên instance đã phát (đủ cho dev).
    static byte[] FALLBACK_KEY = generateFallbackKey();

    BookingProperties bookingProperties;

    @PostConstruct
    void warnIfFallbackKey() {
        if (bookingProperties.getCancellation().getPreviewSecret().isBlank()) {
            log.warn("BOOKING_CANCELLATION_PREVIEW_SECRET is not set — preview tokens are only valid on this instance");
        }
    }

    @Override
    public String sign(UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund, Instant expiresAt) {
        long expiresAtSeconds = expiresAt.getEpochSecond();
        return expiresAtSeconds + TOKEN_SEPARATOR + calculateSignature(bookingId, occurrenceIds, totalRefund, expiresAtSeconds);
    }

    @Override
    public void verify(String previewToken, UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund,
                       Instant now) {
        int separatorIndex = previewToken.indexOf(TOKEN_SEPARATOR);
        if (separatorIndex <= 0) {
            throw new AppException(ErrorCode.CANCELLATION_PREVIEW_EXPIRED);
        }
        long expiresAtSeconds;
        try {
            expiresAtSeconds = Long.parseLong(previewToken.substring(0, separatorIndex));
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.CANCELLATION_PREVIEW_EXPIRED);
        }
        if (expiresAtSeconds <= now.getEpochSecond()) {
            throw new AppException(ErrorCode.CANCELLATION_PREVIEW_EXPIRED);
        }
        String expectedSignature = calculateSignature(bookingId, occurrenceIds, totalRefund, expiresAtSeconds);
        byte[] actual = previewToken.substring(separatorIndex + 1).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), actual)) {
            throw new AppException(ErrorCode.CANCELLATION_PREVIEW_EXPIRED);
        }
    }

    // Buổi sắp xếp theo id, số tiền bỏ số 0 thừa — cùng nội dung luôn cùng chữ ký.
    private String calculateSignature(UUID bookingId, List<UUID> occurrenceIds, BigDecimal totalRefund,
                                      long expiresAtSeconds) {
        String sortedIds = occurrenceIds.stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
        String amount = totalRefund == null ? "" : totalRefund.stripTrailingZeros().toPlainString();
        String payload = bookingId + FIELD_SEPARATOR + sortedIds + FIELD_SEPARATOR + amount + FIELD_SEPARATOR
                + expiresAtSeconds;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveKey(), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private byte[] resolveKey() {
        String previewSecret = bookingProperties.getCancellation().getPreviewSecret();
        return previewSecret.isBlank() ? FALLBACK_KEY : previewSecret.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] generateFallbackKey() {
        byte[] key = new byte[FALLBACK_KEY_BYTES];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
