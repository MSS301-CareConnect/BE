package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.BookingCodeGenerator;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingCodeGeneratorImpl implements BookingCodeGenerator {

    static String PREFIX = "BK-";
    // Base32 bỏ ký tự dễ nhầm (0/O, 1/I/L) — khách đọc mã qua điện thoại.
    static char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    static int SUFFIX_LENGTH = 6;
    static int MAX_ATTEMPTS = 5;
    static DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    static SecureRandom RANDOM = new SecureRandom();

    BookingRepository bookingRepository;
    BookingProperties bookingProperties;
    Clock clock;

    // ~31^6 ≈ 8,8 triệu mã / ngày; trùng (hiếm) → sinh lại. Unique uk_bookings_code ở DB là chốt chặn cuối.
    @Override
    public String generate() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), bookingProperties.getSchedule().getTimezone());
        String datePart = PREFIX + today.format(DATE_FORMAT) + "-";
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = datePart + randomSuffix();
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        log.error("Could not generate a unique booking code after {} attempts", MAX_ATTEMPTS);
        throw new AppException(ErrorCode.SERVICE_UNAVAILABLE);
    }

    private String randomSuffix() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int index = 0; index < SUFFIX_LENGTH; index++) {
            suffix.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return suffix.toString();
    }
}
