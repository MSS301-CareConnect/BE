package mss301.apigateway.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của ApiGateway: 9000–9099 (lỗi hạ tầng tại gateway) + 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service.
// Lỗi nghiệp vụ do service phía sau trả được chuyển nguyên (mã của service đó).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(9000, "Invalid request", HttpStatus.BAD_REQUEST),
    GATEWAY_UPSTREAM_UNAVAILABLE(9001, "Service is temporarily unavailable, please try again later", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(9002, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_UPSTREAM_TIMEOUT(9003, "Service took too long to respond, please try again later", HttpStatus.GATEWAY_TIMEOUT);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
