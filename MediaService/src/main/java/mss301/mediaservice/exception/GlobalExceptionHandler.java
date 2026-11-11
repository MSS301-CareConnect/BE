package mss301.mediaservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.mediaservice.dto.response.APIResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Trả 403 khi thiếu quyền truy cập.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<APIResponse<Void>> handlingAccessDenied(AccessDeniedException exception) {
        return toResponse(ErrorCode.UNAUTHORIZED);
    }

    // Trả lỗi nghiệp vụ theo ErrorCode của AppException.
    @ExceptionHandler(AppException.class)
    public ResponseEntity<APIResponse<Void>> handlingAppException(AppException exception) {
        return toResponse(exception.getErrorCode());
    }

    // Trả 400 khi request không đọc được hoặc sai kiểu tham số.
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<APIResponse<Void>> handlingUnreadableRequest(Exception exception) {
        return toResponse(ErrorCode.INVALID_REQUEST);
    }

    // Trả lỗi khi file upload vượt dung lượng cho phép.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<APIResponse<Void>> handlingMaximumUpload(MaxUploadSizeExceededException exception) {
        return toResponse(ErrorCode.FILE_TOO_LARGE);
    }

    // Trả 404 khi không có endpoint khớp.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<APIResponse<Void>> handlingNoResourceFound(NoResourceFoundException exception) {
        return toResponse(ErrorCode.PATH_NOT_FOUND);
    }

    // Trả 503 khi database lỗi.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<APIResponse<Void>> handlingDataAccess(DataAccessException exception) {
        log.error("Media database operation unavailable ({})", exception.getClass().getSimpleName());
        return toResponse(ErrorCode.DATABASE_UNAVAILABLE);
    }

    // Bắt mọi lỗi chưa xử lý và trả lỗi chung.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled MediaService error ({})", exception.getClass().getName());
        return toResponse(ErrorCode.UNCATEGORIZED_EXCEPTION);
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
