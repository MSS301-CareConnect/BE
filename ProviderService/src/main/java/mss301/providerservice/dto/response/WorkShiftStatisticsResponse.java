package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Số liệu ca của một trung tâm theo bộ lọc (khoảng ngày, loại ca), không theo trang. Trạng thái là trạng thái hiệu
 * lực (WorkShiftStates). Tỉ lệ lấp đầy = approvedTotal / capacityTotal trên các ca OPEN, CLOSED, COMPLETED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorkShiftStatisticsResponse {
    long total;
    long draft;
    long open;
    long closed;
    long completed;
    long cancelled;
    // Ca chưa hủy, bắt đầu trong 7 ngày tới (tính từ bây giờ, không phụ thuộc khoảng ngày lọc).
    long upcoming7Days;
    // Trong số đó, ca còn thiếu người (APPROVED < capacity) và tổng số người còn thiếu — thẻ "cần chú ý" của manager.
    long understaffed7Days;
    long missingStaff7Days;
    // Đăng ký đang chờ duyệt của các ca chưa bắt đầu.
    long pendingRegistrations;
    long capacityTotal;
    long approvedTotal;
}
