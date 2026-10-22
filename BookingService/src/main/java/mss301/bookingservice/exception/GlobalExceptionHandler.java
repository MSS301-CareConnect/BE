package mss301.bookingservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.response.APIResponse;
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

import java.util.Locale;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Tên unique constraint trong schema/booking.sql
    private static final String DETAIL_SERVICE_CONSTRAINT = "uk_booking_details_service";
    private static final String CAREGIVER_SLOT_CONSTRAINT = "ex_booking_occurrences_caregiver_slot";

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

    // Hai thao tác cùng đổi một booking / buổi: bản ghi sau thua ở @Version hoặc khóa dòng.
    @ExceptionHandler({OptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<APIResponse<Void>> handlingLockingFailure(DataAccessException exception) {
        return toResponse(ErrorCode.CONCURRENT_UPDATE);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataIntegrityViolation(DataIntegrityViolationException exception) {
        // Constraint ở DB là chốt chặn cuối khi hai request song song cùng qua bước kiểm tra trước.
        String detail = exception.getMostSpecificCause().getMessage();
        String normalizedDetail = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (normalizedDetail.contains(DETAIL_SERVICE_CONSTRAINT)) {
            return toResponse(ErrorCode.DUPLICATE_SERVICE);
        }
        if (normalizedDetail.contains(CAREGIVER_SLOT_CONSTRAINT)) {
            return toResponse(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT);
        }
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        // Không log message của driver (có thể chứa dữ liệu người bệnh trong snapshot), chỉ log loại lỗi.
        log.error("Booking database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled BookingService error ({})", exception.getClass().getName());
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
