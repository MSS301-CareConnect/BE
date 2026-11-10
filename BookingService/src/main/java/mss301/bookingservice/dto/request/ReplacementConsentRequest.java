package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReplacementConsentRequest {

    /**
     * Hành động:
     * - ACCEPT: Đồng ý chuyên viên thay thế
     * - RESCHEDULE: Dời buổi sang thời gian mới (kèm newStartAt, newEndAt)
     * - REJECT_AND_CANCEL: Từ chối chuyên viên thay thế & hủy buổi (hoàn tiền 100%)
     */
    @NotBlank(message = "ACTION_REQUIRED")
    String action;

    Instant newStartAt;

    Instant newEndAt;
}
