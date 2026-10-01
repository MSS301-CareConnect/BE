package mss301.identityservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi trong security filter không tới @ControllerAdvice — class này trả CÙNG envelope APIResponse:
 * token thiếu/sai → 401, thiếu quyền → 403, thiếu/sai CSRF → 403 CSRF_INVALID, Redis/DB không khả dụng → 503
 * (không giả làm 401).
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler {

    ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        ErrorCode errorCode = exception instanceof AuthenticationServiceException
                ? ErrorCode.AUTH_DEPENDENCY_UNAVAILABLE
                : ErrorCode.UNAUTHENTICATED;
        writeError(response, errorCode);
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        commence(request, response, exception);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        // CsrfFilter cũng báo lỗi qua AccessDeniedException → tách mã riêng để không bị hiểu nhầm là thiếu quyền.
        ErrorCode errorCode = exception instanceof CsrfException
                ? ErrorCode.CSRF_INVALID
                : ErrorCode.UNAUTHORIZED;
        writeError(response, errorCode);
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        APIResponse<Void> body = APIResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        response.setStatus(errorCode.getStatusCode().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
