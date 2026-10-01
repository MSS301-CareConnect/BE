package mss301.identityservice.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bind userId (UUID, = {@code sub} của access token) của người đang gọi vào tham số controller —
 * controller không tự parse {@code authentication.getName()}. Resolver: {@link CurrentUserIdArgumentResolver}.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
