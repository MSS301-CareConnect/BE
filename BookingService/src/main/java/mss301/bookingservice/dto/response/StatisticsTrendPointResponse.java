package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// Một điểm biểu đồ xu hướng: period = yyyy-MM-dd (DAY) hoặc yyyy-MM (MONTH), giờ Việt Nam. bookings đếm theo lúc tạo,
// các buổi đếm theo giờ bắt đầu — cùng mốc thời gian với các số tổng của kỳ.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatisticsTrendPointResponse {
    String period;
    long bookings;
    long completedVisits;
    long cancelledVisits;
    long missedVisits;
}
