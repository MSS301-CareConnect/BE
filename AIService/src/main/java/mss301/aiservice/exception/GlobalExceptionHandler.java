package mss301.aiservice.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import mss301.aiservice.dto.response.APIResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<APIResponse<Void>> handleAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        if (errorCode == null) {
            errorCode = ErrorCode.UNCATEGORIZED_EXCEPTION;
        }

        return toResponse(errorCode);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<APIResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return toResponse(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class, ConstraintViolationException.class})
    public ResponseEntity<APIResponse<Void>> handleInvalidRequest(Exception exception) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        String errorKey = exception
                .getBindingResult()
                .getAllErrors()
                .getFirst()
                .getDefaultMessage();

        try {
            ErrorCode errorCode = ErrorCode.valueOf(errorKey);
            return toResponse(errorCode);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return toResponse(ErrorCode.INVALID_KEY);
        }
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<APIResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return toResponse(ErrorCode.TOTAL_IMAGE_SIZE_TOO_LARGE);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<APIResponse<Void>> handleMultipart(MultipartException exception) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handleDataIntegrity(DataIntegrityViolationException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<APIResponse<Void>> handlePathNotFound(Exception exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handleDataAccess(DataAccessException exception) {
        log.error("AI database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled AIService error ({})", exception.getClass().getName());
        return toResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private ResponseEntity<APIResponse<Void>> toResponse(ErrorCode errorCode) {
        APIResponse<Void> response = APIResponse.<Void>builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity
                .status(errorCode.getStatusCode())
                .body(response);
    }
}
