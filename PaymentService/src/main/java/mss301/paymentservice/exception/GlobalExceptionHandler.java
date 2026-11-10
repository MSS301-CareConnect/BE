package mss301.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.response.APIResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<APIResponse<Void>> handlingAppException(AppException exception) {
        return toResponse(exception.getErrorCode());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<APIResponse<Void>> handlingAuthenticationException(AuthenticationException exception) {
        return toResponse(ErrorCode.UNAUTHENTICATED);
    }

    // @PreAuthorize từ chối trong controller — không để rơi xuống handler chung thành 500 (H-14).
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    public ResponseEntity<APIResponse<Void>> handlingUnreadableRequest(Exception exception) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        // message của Bean Validation chính là key ErrorCode; key lạ → INVALID_KEY để dễ phát hiện khi dev.
        String key = exception.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        try {
            return toResponse(ErrorCode.valueOf(key));
        } catch (IllegalArgumentException | NullPointerException e) {
            return toResponse(ErrorCode.INVALID_KEY);
        }
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    // Hai callback / hai lần tạo phiên cùng đổi một payment: bản ghi sau thua ở @Version hoặc khóa dòng.
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class,
            DataIntegrityViolationException.class})
    public ResponseEntity<APIResponse<Void>> handlingConcurrentUpdate(DataAccessException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        log.error("Payment database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled PaymentService error ({})", exception.getClass().getName());
        return toResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private ResponseEntity<APIResponse<Void>> toResponse(ErrorCode errorCode) {
        APIResponse<Void> response = APIResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
