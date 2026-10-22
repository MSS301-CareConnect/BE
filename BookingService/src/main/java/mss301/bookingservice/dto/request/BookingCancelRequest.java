package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// BK-03 / BK-04: hủy cả booking hoặc từng buổi. occurrenceIds rỗng = mọi buổi chưa diễn ra. previewToken (khách) lấy
// từ cancellation-previews — kết quả tính lại khác lúc preview thì 409 để FE hỏi lại khách.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BookingCancelRequest {
    @NotBlank(message = "REASON_REQUIRED")
    @Size(max = 1000, message = "TEXT_TOO_LONG")
    String reason;

    @Size(max = 500, message = "TOO_MANY_OCCURRENCES")
    List<UUID> occurrenceIds;

    @Size(max = 200, message = "CANCELLATION_PREVIEW_EXPIRED")
    String previewToken;
}
