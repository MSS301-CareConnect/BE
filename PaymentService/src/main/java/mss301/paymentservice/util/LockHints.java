package mss301.paymentservice.util;

/**
 * Hint khóa dòng dùng chung cho repository (mẫu BookingService). Hibernate hiểu lock timeout -2 là SKIP LOCKED
 * (PostgreSQL: {@code FOR UPDATE SKIP LOCKED}) — dòng đang bị transaction khác khóa thì bỏ qua thay vì chờ.
 */
public final class LockHints {

    public static final String LOCK_TIMEOUT = "jakarta.persistence.lock.timeout";
    public static final String SKIP_LOCKED = "-2";

    private LockHints() {
    }
}
