package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.MissedReason;
import mss301.bookingservice.enums.OccurrenceStatus;

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

    Integer durationMinutes;

    OccurrenceStatus status;

    UUID caregiverId;

    UUID caregiverUserId;

    UUID bookingId;

    String bookingCode;

    BookingStatus bookingStatus;

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
}
