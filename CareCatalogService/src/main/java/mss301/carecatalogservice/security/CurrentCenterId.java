package mss301.carecatalogservice.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tiêm centerId (UUID) của người dùng hiện tại (CENTER_MANAGER) từ claim center_id trong JWT.
 * Bị ẩn khỏi Swagger thông qua {@link mss301.carecatalogservice.config.swagger.OpenApiConfig}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentCenterId {
    // false: endpoint nhiều role — thiếu claim center_id thì trả null, service tự quyết phạm vi.
    boolean required() default true;
}
