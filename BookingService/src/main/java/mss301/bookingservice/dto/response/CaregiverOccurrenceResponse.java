package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.BookingType;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Buổi chăm sóc kèm thông tin booking cho caregiver / service nội bộ. recipientSnapshot chỉ có ở chi tiết.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaregiverOccurrenceResponse {
    UUID id;

    Integer occurrenceNo;

    Instant startAt;

    Instant endAt;

    // Q12: cửa sổ check-in theo center policy chụp trên booking (sau checkInClosesAt chưa check-in → CAREGIVER_MISSED).
    Instant checkInOpensAt;

    Instant checkInClosesAt;

    // 2026-11-13: buổi CAREGIVER_MISSED — giờ khoản hoàn 100 % sẽ gửi; còn giá trị = trung tâm còn ghi nhận check-in hộ được.
    Instant missedRefundAt;

    Integer durationMinutes;

    // Dịch vụ của buổi (NULL = mọi dịch vụ của booking).
    UUID careServiceId;

    OccurrenceStatus status;

    UUID caregiverId;

    UUID caregiverUserId;

    UUID bookingId;

    String bookingCode;

    BookingStatus bookingStatus;

    BookingType bookingType;

    // SHIFT: lịch caregiver (ca làm việc ở ProviderService) mà buổi giữ.
    UUID caregiverScheduleId;

    // Chờ trung tâm phân người khác (caregiver đã xin hủy buổi, hoặc tài khoản bị khóa) — check-in bị chặn (2026-11-09).
    Boolean needsReassignment;

    // Giá trị buổi theo bảng giá lúc đặt.
    BigDecimal priceAmount;

    UUID centerId;

    UUID recipientId;

    String recipientName;

    String serviceAddress;

    String customerNote;

    Instant acknowledgedAt;

    // OPS-06: lý do MISSED và báo khách vắng mặt (null khi không có).
    MissedReason missedReason;

    Instant noShowReportedAt;

    String noShowNote;

    Instant noShowDecidedAt;

    RecipientSnapshot recipientSnapshot;

    // Chỉ có ở chi tiết một buổi (OPS-01).
    List<OccurrenceServiceResponse> services;

    SpecialNotesResponse specialNotes;

    // Caregiver chỉ thấy đủ số khi buổi bắt đầu trong 24h tới; trước đó bị che (OPS-01).
    String customerPhone;

    // Care session của buổi (OPS-02) — null khi chưa check-in.
    UUID sessionId;

    // Trạng thái care report (OPS-05, người C) — null tới khi có module report.
    String reportStatus;

    // Chỉ có ở chi tiết một buổi: họ tên khách đặt (Identity) và các buổi của cùng caregiver trong booking.
    String customerName;

    List<OccurrenceResponse> bookingVisits;
}
