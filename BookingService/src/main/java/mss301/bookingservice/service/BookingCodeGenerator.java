package mss301.bookingservice.service;

// Mã booking dễ đọc, duy nhất: BK-yyyyMMdd-XXXXXX (ngày theo booking.schedule.timezone).
public interface BookingCodeGenerator {
    String generate();
}
