package mss301.providerservice.security;

import java.lang.annotation.*;

/**
 * Bind userId (UUID, = {@code sub} của access token) của người đang gọi vào tham số controller —
 * controller không tự parse {@code authentication.getName()}. Resolver: {@link CurrentUserIdArgumentResolver}.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
