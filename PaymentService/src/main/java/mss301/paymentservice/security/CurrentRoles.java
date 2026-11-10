package mss301.paymentservice.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bind role của người gọi (claim roles[]) vào {@code Set<RoleCode>} để service chọn phạm vi dữ liệu — controller không
 * tự so chuỗi {@code ROLE_*}. Role lạ bị bỏ qua. Resolver: {@link CurrentRolesArgumentResolver}.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentRoles {
}
