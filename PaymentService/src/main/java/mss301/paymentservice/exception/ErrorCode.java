package mss301.paymentservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(6000, "Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(6001, "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(6002, "Bạn chưa đăng nhập", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(6003, "Bạn không có quyền thực hiện", HttpStatus.FORBIDDEN),
    PAYMENT_NOT_FOUND(6004, "Không tìm thấy giao dịch", HttpStatus.NOT_FOUND),
    BOOKING_NOT_PAYABLE(6005, "Booking không ở trạng thái chờ thanh toán", HttpStatus.CONFLICT),
    PAYMENT_ALREADY_SUCCESS(6006, "Booking đã được thanh toán", HttpStatus.CONFLICT),
    PAYMENT_PROVIDER_DISABLED(6007, "Cổng thanh toán chưa được cấu hình", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_SIGNATURE(6008, "Chữ ký callback không hợp lệ", HttpStatus.BAD_REQUEST),
    AMOUNT_MISMATCH(6009, "Số tiền callback không khớp", HttpStatus.CONFLICT),
    BOOKING_SERVICE_UNAVAILABLE(6010, "BookingService tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(6011, "Không lấy được token dành cho service", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_GATEWAY_ERROR(6012, "Không tạo được phiên thanh toán", HttpStatus.BAD_GATEWAY),
    PATH_NOT_FOUND(6013, "Đường dẫn không tồn tại", HttpStatus.NOT_FOUND);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
