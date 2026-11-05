package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.RecurrenceFrequency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// CR-3: đơn khách gửi thẳng cho caregiver. Chưa xác nhận nên chỉ lộ khu vực (phường / quận / tỉnh), không số nhà / SĐT /
// liên hệ khẩn cấp / danh sách thuốc — hồ sơ đầy đủ chỉ mở khi booking CONFIRMED (AGENTS.md BookingService rule 7).
// 2026-11-05 (chủ dự án chọn): thêm tên + tình trạng sức khỏe từ snapshot để caregiver đủ thông tin quyết định nhận.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaregiverBookingRequestResponse {
    UUID id;

    String bookingCode;

    BookingStatus status;

    BookingType bookingType;

    RecurrenceFrequency recurrenceFrequency;

    UUID centerId;

    LocalDate startDate;

    LocalDate endDate;

    LocalTime startTime;

    LocalTime endTime;

    List<Integer> daysOfWeek;

    Integer recipientAge;

    String recipientGender;

    String recipientName;

    String mobilityLevel;

    String independenceLevel;

    String chronicConditions;

    String allergies;

    String careRequirements;

    String dietaryNotes;

    String behaviorNotes;

    // Chỉ số thuốc đang dùng; tên / liều mở khi booking CONFIRMED.
    Integer medicationCount;

    // Họ tên khách đặt (Identity); thiếu khi Identity lỗi.
    String customerName;

    String ward;

    String district;

    String province;

    String customerNote;

    BigDecimal totalAmount;

    // Hạn caregiver trả lời; quá hạn thì RECURRING / CUSTOM chuyển trung tâm, SHIFT hết hạn.
    Instant acceptanceDueAt;

    Instant createdAt;

    List<BookingItemResponse> services;

    List<OccurrenceResponse> occurrences;
}
