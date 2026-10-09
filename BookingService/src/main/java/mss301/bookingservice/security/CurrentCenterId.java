package mss301.bookingservice.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bind claim {@code center_id} của access token (trung tâm của CENTER_MANAGER) vào tham số controller.
 * Thiếu / sai claim → 403 CENTER_SCOPE_REQUIRED. Resolver: {@link CurrentCenterIdArgumentResolver}.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCenterId {
}
