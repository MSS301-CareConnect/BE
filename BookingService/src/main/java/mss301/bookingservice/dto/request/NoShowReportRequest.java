package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Caregiver báo khách vắng mặt (OPS-06): mô tả đã chờ / liên hệ thế nào để manager có căn cứ quyết định.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoShowReportRequest {
    @NotBlank(message = "NO_SHOW_NOTE_REQUIRED")
    @Size(min = 10, max = 1000, message = "NO_SHOW_NOTE_REQUIRED")
    String note;
}
