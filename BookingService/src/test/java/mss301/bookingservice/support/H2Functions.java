package mss301.bookingservice.support;

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
