package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.CareSessionStatus;
import mss301.bookingservice.enums.SessionCompletionReview;
import mss301.bookingservice.enums.SessionLocationFlag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Ca chăm sóc + checklist (OPS-02 / OPS-04).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareSessionResponse {
    UUID sessionId;

    UUID occurrenceId;

    UUID bookingId;

    UUID caregiverId;

    CareSessionStatus status;

    Instant checkInAt;

    Instant checkOutAt;

    UUID endedBy;

    String endReason;

    boolean autoClosed;
    // 2026-11-11: chấm công thực tế và cờ để trung tâm xem lại.
    int lateMinutes;
    Integer earlyLeaveMinutes;
    Integer checkInDistanceMeters;
    SessionLocationFlag locationFlag;
    SessionCompletionReview completionReview;
    Instant reviewedAt;
    String reviewNote;
    // Lý do khách phản ánh buổi (nếu ca chờ xác nhận vì khách báo, không phải vì hệ thống tự đóng).
    String customerReportNote;
    // Buổi của ca — để hàng đợi "chờ trung tâm xác nhận" hiển thị mà không phải đọc thêm booking.
    String bookingCode;
    String recipientName;
    Integer occurrenceNo;
    Instant startAt;
    Instant endAt;

    List<CareTaskResponse> tasks;
}
