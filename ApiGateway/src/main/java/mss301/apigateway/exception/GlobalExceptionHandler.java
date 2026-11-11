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
import java.util.Arrays;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Trả 404 dạng APIResponse khi không có route khớp.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    // Trả lỗi khi gọi sai HTTP method cho route.
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

    // Bắt mọi lỗi chưa xử lý: chỉ log tên lỗi + stack, trả lỗi chung.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        // Chỉ ghi frame, không ghi message có thể chứa URL/header nhạy cảm từ dependency.
        log.error("Unhandled ApiGateway error ({}), frames={}", exception.getClass().getName(),
                Arrays.toString(exception.getStackTrace()));
        return toResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    // Kiểm tra chuỗi nguyên nhân của exception có chứa loại lỗi cho trước.
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

    // Dựng ResponseEntity APIResponse từ ErrorCode.
    private ResponseEntity<APIResponse<Void>> toResponse(ErrorCode errorCode) {
        APIResponse<Void> response = APIResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
