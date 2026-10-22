package mss301.carecatalogservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.dto.response.APIResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String CENTER_SERVICE_UNIQUE_INDEX = "uk_center_services_center_care";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<APIResponse<Void>> handlingAppException(AppException exception) {
        return toResponse(exception.getErrorCode());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<APIResponse<Void>> handlingAuthenticationException(AuthenticationException exception) {
        return toResponse(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<Void>> handlingAccessDeniedException(AccessDeniedException exception) {
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
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

    // Hai người cùng sửa một bản ghi: bản ghi sau thua ở @Version.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse<Void>> handlingOptimisticLocking(OptimisticLockingFailureException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataIntegrityViolation(DataIntegrityViolationException exception) {
        // Constraint ở DB là chốt chặn cuối (FK, NOT NULL, unique) khi bước kiểm tra trong service bị lọt — vd hai request
        // song song cùng đăng ký một dịch vụ chuẩn cho một trung tâm.
        String detail = exception.getMostSpecificCause().getMessage();
        if (detail != null && detail.toLowerCase(Locale.ROOT).contains(CENTER_SERVICE_UNIQUE_INDEX)) {
            return toResponse(ErrorCode.CENTER_SERVICE_ALREADY_EXISTS);
        }
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        // Không log message của driver, chỉ log loại lỗi.
        log.error("Care catalog database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled CareCatalogService error ({})", exception.getClass().getName());
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
