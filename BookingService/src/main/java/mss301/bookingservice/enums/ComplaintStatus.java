package mss301.bookingservice.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ComplaintStatus {
    OPEN, IN_PROGRESS, RESOLVED, ESCALATED, RESOLVED_BY_ADMIN,
    // D20: khách rút khiếu nại khi chưa được giải quyết.
    WITHDRAWN;

    // Khiếu nại còn chờ xử lý — mỗi buổi (hoặc mỗi booking khi không gắn buổi) tối đa một khiếu nại ở các trạng thái này.
    public static final Set<ComplaintStatus> ACTIVE = EnumSet.of(OPEN, IN_PROGRESS, ESCALATED);
}
