package mss301.bookingservice.security;

import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class CurrentCenterIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentCenterId.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        // Phạm vi trung tâm chỉ lấy từ token (Identity phát), không bao giờ từ request.
        String centerId = jwtAuthentication.getToken().getClaimAsString(TokenClaims.CENTER_ID);
        if (centerId == null || centerId.isBlank()) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
        try {
            return UUID.fromString(centerId);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.CENTER_SCOPE_REQUIRED);
        }
    }
}
