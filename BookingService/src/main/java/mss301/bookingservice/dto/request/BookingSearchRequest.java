package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

// Query của GET /api/v1/bookings (bind từ query string). Phạm vi theo role do service áp lên trên các filter này:
// CUSTOMER luôn bị khóa vào booking của mình, CENTER_MANAGER vào trung tâm trong token; chỉ ADMIN lọc tự do.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingSearchRequest {
    BookingStatus status;

    UUID recipientId;

    UUID centerId;

    UUID customerId;

    UUID caregiverId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate to;

    // Tìm theo mã booking (chứa chuỗi, không phân biệt hoa thường).
    @Size(max = 50, message = "TEXT_TOO_LONG")
    String keyword;

    // "createdAt,desc" (mặc định) | "startDate,asc"… — whitelist ở BookingSorts.
    String sort;

    @Builder.Default
    int page = 0;

    @Builder.Default
    int size = 20;
}
