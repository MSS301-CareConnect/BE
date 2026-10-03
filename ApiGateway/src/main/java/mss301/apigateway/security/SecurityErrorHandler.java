package mss301.apigateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.apigateway.dto.response.APIResponse;
import mss301.apigateway.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi trong filter (không tới @ControllerAdvice) trả CÙNG envelope APIResponse với các service:
 * token thiếu/sai → 401, thiếu quyền → 403, không lấy được JWKS của Identity → 503, URL bị firewall từ chối → 400.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler, AuthenticationFailureHandler,
        RequestRejectedHandler {

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
        writeError(response, ErrorCode.UNAUTHORIZED);
    }

    // URL không chuẩn hóa / ký tự bị cấm (vd "..", "%2e", ";") — StrictHttpFirewall chặn trước khi tới route.
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       RequestRejectedException exception) throws IOException {
        writeError(response, ErrorCode.INVALID_REQUEST);
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
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
