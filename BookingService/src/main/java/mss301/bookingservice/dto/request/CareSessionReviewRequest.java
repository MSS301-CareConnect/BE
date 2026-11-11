package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// POST /api/v1/care-sessions/{id}/review — trung tâm kết luận ca hệ thống tự đóng: đã thực hiện (quyết toán) hay không
// (hoàn tiền khách, ghi vắng ca). Ghi chú bắt buộc khi kết luận không thực hiện.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionReviewRequest {
    @NotNull(message = "SESSION_REVIEW_DECISION_REQUIRED")
    Boolean delivered;

    @Size(max = 1000, message = "SESSION_REVIEW_NOTE_INVALID")
    String note;
}
