package mss301.aiservice.service.impl;

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

    private GuestDailyUsage createNewUsage(String visitorHash, LocalDate usageDate) {
        return GuestDailyUsage.builder()
                .visitorHash(visitorHash)
                .usageDate(usageDate)
                .promptCount(0)
                .build();
    }

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
