package mss301.aiservice.service.Impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.entity.GuestDailyUsage;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.repository.GuestDailyUsageRepository;
import mss301.aiservice.service.GuestUsageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GuestUsageServiceImpl implements GuestUsageService {
    static String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    static String USER_AGENT_HEADER = "User-Agent";

    GuestDailyUsageRepository guestDailyUsageRepository;
    AIProperties aiProperties;
    Clock clock;

    // Trừ một lượt chat của khách trong ngày (theo IP + user agent), hết lượt thì báo lỗi.
    @Override
    @Transactional
    public int consumePrompt(HttpServletRequest request) {
        String visitorHash = createVisitorHash(request);
        LocalDate usageDate = LocalDate.now(clock.withZone(aiProperties.getChat().getGuestTimezone()));

        GuestDailyUsage usage = guestDailyUsageRepository
                .findByVisitorHashAndUsageDateAndDeletedAtIsNull(visitorHash, usageDate)
                .orElseGet(() -> createNewUsage(visitorHash, usageDate));

        int limit = aiProperties
                .getChat()
                .getGuestDailyPromptLimit();

        int currentPromptCount = usage.getPromptCount() == null ? 0 : usage.getPromptCount();

        if (currentPromptCount >= limit) {
            throw new AppException(ErrorCode.GUEST_DAILY_LIMIT_EXCEEDED);
        }

        int newPromptCount = currentPromptCount + 1;
        usage.setPromptCount(newPromptCount);
        guestDailyUsageRepository.save(usage);
        return limit - newPromptCount;
    }

    // Hoàn lại một lượt chat của khách trong ngày.
    @Override
    public void restorePrompt(HttpServletRequest request) {
        String visitorHash = createVisitorHash(request);

        LocalDate usageDate = LocalDate.now(clock.withZone(aiProperties.getChat().getGuestTimezone()));

        guestDailyUsageRepository.findByVisitorHashAndUsageDateAndDeletedAtIsNull(visitorHash, usageDate)
                .ifPresent(usage -> {
                    int currentCount = usage.getPromptCount() == null ? 0 : usage.getPromptCount();

                    if (currentCount > 0) {
                        usage.setPromptCount(currentCount - 1);
                        guestDailyUsageRepository.save(usage);
                    }
                });
    }

    // Tính số lượt chat còn lại trong ngày của khách.
    @Override
    @Transactional(readOnly = true)
    public int getRemainingPrompts(HttpServletRequest request) {
        String visitorHash = createVisitorHash(request);
        LocalDate usageDate = LocalDate.now(clock.withZone(aiProperties.getChat().getGuestTimezone()));
        int limit = aiProperties.getChat().getGuestDailyPromptLimit();

        int usedPrompts = guestDailyUsageRepository
                .readByVisitorHashAndUsageDateAndDeletedAtIsNull(visitorHash, usageDate)
                .map(GuestDailyUsage::getPromptCount)
                .orElse(0);

        return Math.max(0, limit - usedPrompts);
    }

    // Tạo bản ghi lượt dùng mới trong ngày của khách.
    private GuestDailyUsage createNewUsage(String visitorHash, LocalDate usageDate) {
        return GuestDailyUsage.builder()
                .visitorHash(visitorHash)
                .usageDate(usageDate)
                .promptCount(0)
                .build();
    }

    // Băm IP + user agent thành mã nhận diện khách.
    private String createVisitorHash(HttpServletRequest request) {
        String clientAddress = resolveClientAddress(request);
        String userAgent = request.getHeader(USER_AGENT_HEADER);

        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "unknown";
        }

        if (userAgent.length() > 500) {
            userAgent = userAgent.substring(0, 500);
        }

        String rawIdentity = clientAddress + "|" + userAgent;
        return sha256(rawIdentity);
    }

    // Lấy IP client từ X-Forwarded-For hoặc địa chỉ kết nối.
    private String resolveClientAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstAddress = forwardedFor.split(",")[0].trim();

            if (!firstAddress.isBlank()) {
                return firstAddress;
            }
        }

        String remoteAddress = request.getRemoteAddr();

        return remoteAddress == null
                || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress;
    }

    // Băm SHA-256 một chuỗi sang hex.
    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new AppException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }
    }
}
