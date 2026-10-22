package mss301.carecatalogservice.security;

import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String centerIdClaim = jwtAuth.getToken().getClaimAsString(TokenClaims.CENTER_ID);
            if (centerIdClaim == null || centerIdClaim.isBlank()) {
                if (!parameter.getParameterAnnotation(CurrentCenterId.class).required()) {
                    return null;
                }
                throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
            }
            try {
                return UUID.fromString(centerIdClaim);
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.CENTER_ACCESS_DENIED);
            }
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
