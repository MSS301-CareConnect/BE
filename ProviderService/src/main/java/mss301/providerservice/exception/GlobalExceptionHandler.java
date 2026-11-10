package mss301.providerservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.response.APIResponse;
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

    static String CENTER_EMAIL_CONSTRAINT = "uk_care_centers_email";
    static String CENTER_MANAGER_CONSTRAINT = "uk_care_centers_manager_active";
    static String CAREGIVER_USER_CONSTRAINT = "uk_caregiver_profiles_user";
    static String REGISTRATION_CONSTRAINT = "uk_shift_registrations_shift_caregiver";

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
        String key = exception.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        try {
            return toResponse(ErrorCode.valueOf(key));
        } catch (IllegalArgumentException | NullPointerException e) {
            return toResponse(ErrorCode.INVALID_REQUEST);
        }
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse<Void>> handlingOptimisticLocking(OptimisticLockingFailureException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataIntegrityViolation(DataIntegrityViolationException exception) {
        String detail = exception.getMostSpecificCause().getMessage();
        String normalizedDetail = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalizedDetail.contains(CENTER_EMAIL_CONSTRAINT)) {
            return toResponse(ErrorCode.CENTER_EMAIL_EXISTED);
        }
        if (normalizedDetail.contains(CENTER_MANAGER_CONSTRAINT)) {
            return toResponse(ErrorCode.CENTER_MANAGER_EXISTED);
        }
        if (normalizedDetail.contains(CAREGIVER_USER_CONSTRAINT)) {
            return toResponse(ErrorCode.CAREGIVER_USER_EXISTED);
        }
        if (normalizedDetail.contains(REGISTRATION_CONSTRAINT)) {
            return toResponse(ErrorCode.REGISTRATION_EXISTED);
        }
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        log.error("Provider database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled ProviderService error ({})", exception.getClass().getName());
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
