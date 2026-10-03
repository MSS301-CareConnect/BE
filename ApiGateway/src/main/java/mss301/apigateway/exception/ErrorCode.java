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
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(9000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    GATEWAY_UPSTREAM_UNAVAILABLE(9001, "Dịch vụ tạm thời không khả dụng, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(9002, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    GATEWAY_UPSTREAM_TIMEOUT(9003, "Dịch vụ phản hồi quá lâu, vui lòng thử lại sau", HttpStatus.GATEWAY_TIMEOUT);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
