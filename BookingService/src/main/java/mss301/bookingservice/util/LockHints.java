package mss301.bookingservice.util;

public final class LockHints {

    public static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";
    public static final String SKIP_LOCKED = "-2";

    // Lớp chỉ chứa hằng gợi ý khóa dòng, không cho khởi tạo.
    private LockHints() {
    }
}
