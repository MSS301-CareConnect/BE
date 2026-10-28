package mss301.paymentservice.security;

import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;

import java.util.UUID;

public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class) && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                NativeWebRequest request, WebDataBinderFactory factory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
