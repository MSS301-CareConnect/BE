package mss301.providerservice.service;

/**
 * Vòng đời tự động của ca (WorkShiftLifecycleScheduler): OPEN quá hạn đăng ký / đã bắt đầu → CLOSED, ca đã kết thúc →
 * COMPLETED, đăng ký còn chờ khi ca đã bắt đầu → REJECTED. Đọc vẫn đúng giữa hai lần chạy nhờ util/WorkShiftStates.
 */
public interface WorkShiftLifecycleService {

    void advance();
}
