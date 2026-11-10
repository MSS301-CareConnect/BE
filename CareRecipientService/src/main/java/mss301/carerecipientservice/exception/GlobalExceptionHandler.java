package mss301.carerecipientservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.dto.response.APIResponse;
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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Tên unique constraint / index trong schema/care_recipient.sql
    private static final String MANAGER_USER_CONSTRAINT = "uk_recipient_managers_user";
    private static final String MANAGER_PRIMARY_INDEX = "uk_recipient_managers_primary";
    private static final String CONTACT_PRIMARY_INDEX = "uk_emergency_contacts_primary";

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

    // Sai method trên path có thật (vd POST /{id}/managers đã thay bằng /invitations, C-04) — 404 như path không tồn tại,
    // không rơi xuống handler chung thành 500.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<APIResponse<Void>> handlingMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    // Hai người cùng sửa một hồ sơ: bản ghi sau thua ở @Version.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<APIResponse<Void>> handlingOptimisticLocking(OptimisticLockingFailureException exception) {
        return toResponse(ErrorCode.RECIPIENT_VERSION_CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataIntegrityViolation(DataIntegrityViolationException exception) {
        // Hai request song song có thể cùng qua bước kiểm tra trước — constraint ở DB là chốt chặn cuối.
        String detail = exception.getMostSpecificCause().getMessage();
        String normalizedDetail = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalizedDetail.contains(MANAGER_USER_CONSTRAINT)) {
            return toResponse(ErrorCode.MANAGER_EXISTED);
        }
        if (normalizedDetail.contains(MANAGER_PRIMARY_INDEX) || normalizedDetail.contains(CONTACT_PRIMARY_INDEX)) {
            return toResponse(ErrorCode.CONCURRENT_UPDATE);
        }
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        // Không log message của driver (có thể chứa dữ liệu người bệnh), chỉ log loại lỗi.
        log.error("Care recipient database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled CareRecipientService error ({})", exception.getClass().getName());
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
