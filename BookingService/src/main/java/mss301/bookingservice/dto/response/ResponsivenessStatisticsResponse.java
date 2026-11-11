package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// SLA trung tâm trong kỳ: số booking đã trả lời (xác nhận / từ chối), thời gian trả lời trung bình (phút, null khi chưa có)
// và số booking hết hạn vì trung tâm chưa xác nhận / chưa phân người chăm sóc kịp.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResponsivenessStatisticsResponse {
    long answeredCount;

    Long avgResponseMinutes;

    long expiredCount;
}
