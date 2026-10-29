package mss301.paymentservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Dải mã PaymentService: 6000–6999. Không đổi số / key đã có (6000–6016); mã mới đánh tiếp.
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
    PATH_NOT_FOUND(6013, "Đường dẫn không tồn tại", HttpStatus.NOT_FOUND),
    CONCURRENT_UPDATE(6014, "Giao dịch vừa được cập nhật bởi thao tác khác, vui lòng thử lại", HttpStatus.CONFLICT),
    SERVICE_UNAVAILABLE(6015, "Dịch vụ thanh toán tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_KEY(6016, "Mã lỗi validation không hợp lệ", HttpStatus.BAD_REQUEST),
    BOOKING_NOT_FOUND(6017, "Không tìm thấy booking", HttpStatus.NOT_FOUND),
    BOOKING_PRICE_MISSING(6018, "Booking còn buổi chưa có giá, chưa thể thanh toán", HttpStatus.CONFLICT),
    NO_EFFECTIVE_POLICY(6019, "Chưa có chính sách thanh toán đang hiệu lực", HttpStatus.SERVICE_UNAVAILABLE),
    REFUND_EXCEEDS_PAID(6020, "Số tiền hoàn vượt quá số khách đã thanh toán", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REQUIRED(6021, "Thiếu Idempotency-Key", HttpStatus.BAD_REQUEST),
    REFUND_NOT_FOUND(6022, "Không tìm thấy yêu cầu hoàn tiền", HttpStatus.NOT_FOUND),
    REFUND_NOT_RETRYABLE(6023, "Chỉ thử lại được yêu cầu hoàn tiền đã thất bại", HttpStatus.CONFLICT),
    INVOICE_NOT_FOUND(6024, "Không tìm thấy hóa đơn", HttpStatus.NOT_FOUND),
    INVOICE_NOT_PAYABLE(6025, "Hóa đơn không ở trạng thái chờ thanh toán", HttpStatus.CONFLICT),
    WAIVE_REASON_REQUIRED(6026, "Vui lòng nhập lý do miễn nợ", HttpStatus.BAD_REQUEST),
    CENTER_SCOPE_REQUIRED(6027, "Tài khoản quản lý chưa gắn trung tâm", HttpStatus.FORBIDDEN),
    BOOKING_ID_REQUIRED(6028, "Thiếu mã booking", HttpStatus.BAD_REQUEST),
    REFUND_REASON_REQUIRED(6029, "Thiếu lý do hoàn tiền", HttpStatus.BAD_REQUEST),
    CANCELLED_AT_REQUIRED(6030, "Thiếu thời điểm hủy", HttpStatus.BAD_REQUEST),
    OCCURRENCES_REQUIRED(6031, "Thiếu danh sách buổi", HttpStatus.BAD_REQUEST),
    OCCURRENCE_ID_REQUIRED(6032, "Thiếu mã buổi", HttpStatus.BAD_REQUEST),
    REFUND_AMOUNT_INVALID(6033, "Số tiền hoàn phải lớn hơn 0", HttpStatus.BAD_REQUEST),
    COMPLAINT_ID_REQUIRED(6034, "Thiếu mã khiếu nại", HttpStatus.BAD_REQUEST),
    PROVIDER_REQUIRED(6035, "Vui lòng chọn cổng thanh toán", HttpStatus.BAD_REQUEST),
    TEXT_TOO_LONG(6036, "Nội dung quá dài", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_WAIVABLE(6037, "Chỉ miễn được hóa đơn đang chờ hoặc quá hạn", HttpStatus.CONFLICT),
    BOOKING_COMMAND_REJECTED(6038, "BookingService từ chối cập nhật", HttpStatus.CONFLICT),
    OCCURRENCE_STATUS_REQUIRED(6039, "Thiếu trạng thái cuối của buổi", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
