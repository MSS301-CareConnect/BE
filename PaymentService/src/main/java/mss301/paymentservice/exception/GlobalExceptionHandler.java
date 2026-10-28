package mss301.paymentservice.exception;

import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.dto.response.APIResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<APIResponse<Void>> app(AppException e) {
        return response(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<APIResponse<Void>> validation() {
        return response(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<APIResponse<Void>> notFound() {
        return response(ErrorCode.PATH_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<APIResponse<Void>> other(Exception e) {
        log.error("Unhandled payment error", e);
        return response(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private ResponseEntity<APIResponse<Void>> response(ErrorCode e) {
        return ResponseEntity.status(e.getStatusCode()).body(APIResponse.error(e));
    }
}
