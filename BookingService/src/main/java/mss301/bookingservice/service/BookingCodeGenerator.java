package mss301.bookingservice.service;

// Mã booking dễ đọc, duy nhất: BK-yyyyMMdd-XXXXXX (ngày theo booking.schedule.timezone).
public interface BookingCodeGenerator {
    // Sinh mã booking dễ đọc, không trùng.
    String generate();
}
