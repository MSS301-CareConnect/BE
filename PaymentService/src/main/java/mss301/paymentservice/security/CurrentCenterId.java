package mss301.paymentservice.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bind claim {@code center_id} của access token (trung tâm của CENTER_MANAGER) — mẫu BookingService. Thiếu claim →
 * 403 CENTER_SCOPE_REQUIRED; {@code required = false} thì trả null (endpoint nhiều role, service tự quyết phạm vi).
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCenterId {
    boolean required() default true;
}
