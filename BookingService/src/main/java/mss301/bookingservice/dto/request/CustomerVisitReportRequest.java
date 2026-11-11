package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// POST /api/v1/booking-occurrences/{id}/customer-reports — khách báo nhanh về một buổi (2026-11-11): tới giờ mà chưa thấy
// người chăm sóc đến (ghi chú tùy chọn), hoặc buổi đã kết thúc nhưng không được thực hiện đúng (ghi chú bắt buộc).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerVisitReportRequest {
    @Size(max = 1000, message = "CUSTOMER_REPORT_NOTE_INVALID")
    String note;
}
