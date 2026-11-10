package mss301.bookingservice.support;

/**
 * Hàm PostgreSQL mà BookingRepository.lockScheduleKey dùng, giả lập cho H2 (test mặc định): khóa tư vấn là no-op —
 * test tuần tự nên không cần khóa; khóa thật được kiểm khi chạy -Dbooking.testing.postgres=true.
 * Đăng ký trong src/test/resources/h2-functions.sql.
 */
public final class H2Functions {

    private H2Functions() {
    }

    public static int hashtext(String value) {
        return value.hashCode();
    }

    public static Integer pgAdvisoryXactLock(int key) {
        return null;
    }
}
