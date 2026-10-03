package mss301.apigateway.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.apigateway.dto.response.APIResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

/**
 * Lỗi khi gateway tự xử lý / chuyển tiếp (không phải phản hồi lỗi của service — phản hồi đó được chuyển nguyên).
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handlingMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    // Không kết nối được / timeout tới service phía sau.
    @ExceptionHandler({ResourceAccessException.class, UncheckedIOException.class, IOException.class})
    public ResponseEntity<APIResponse<Void>> handlingUpstreamFailure(Exception exception) {
        boolean timeout = hasCause(exception, SocketTimeoutException.class) || hasCause(exception, HttpTimeoutException.class);
        log.warn("Upstream call failed ({}{})", exception.getClass().getSimpleName(), timeout ? ", timeout" : "");
        return toResponse(timeout ? ErrorCode.GATEWAY_UPSTREAM_TIMEOUT : ErrorCode.GATEWAY_UPSTREAM_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled ApiGateway error ({})", exception.getClass().getName());
        return toResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ResponseEntity<APIResponse<Void>> toResponse(ErrorCode errorCode) {
        APIResponse<Void> response = APIResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
