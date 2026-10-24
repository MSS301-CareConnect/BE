package mss301.identityservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của IdentityService: 1000–1999 (+ 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    LOCK_REASON_REQUIRED(1118, "Phải nhập lý do khóa tài khoản", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FILTER(1119, "Khoảng ngày không hợp lệ hoặc vượt quá 366 ngày", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    //USER ERRORS
    EMAIL_EXISTED(1003, "Email đã được sử dụng, hãy sử dụng email khác", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1006, "Tên đăng nhập hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1007, "Tên đăng nhập hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    PASSWORD_REQUIRED(1009, "Password không được để trống", HttpStatus.BAD_REQUEST),
    FULLNAME_REQUIRED(1010, "Họ tên không được để trống", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1011, "Email không được để trống", HttpStatus.BAD_REQUEST),
    PHONE_REQUIRED(1012, "Số điện thoại không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_ID_REQUIRED(1013, "Role ID không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(1014, "Số điện thoại phải có 10-11 chữ số", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1015, "Email không đúng định dạng (ví dụ: 112@gmail.com)", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1016, "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    INVALID_FULLNAME(1017, "Họ tên không được vượt quá 255 ký tự", HttpStatus.BAD_REQUEST),

    // ROLE ERRORS
    ROLE_NOT_FOUND(1019, "Role không tồn tại", HttpStatus.NOT_FOUND),
    DEFAULT_ROLE_NOT_FOUND(1020, "Không tìm thấy role mặc định", HttpStatus.NOT_FOUND),
    USER_INACTIVE(1021, "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên để mở lại.", HttpStatus.FORBIDDEN),

    DELETE_ADMIN_INVALID(1022, "Không thể xóa tài khoản Quản trị viên hệ thống", HttpStatus.BAD_REQUEST),
    DELETE_SELF_INVALID(1023, "Bạn không thể tự khóa tài khoản của chính mình!", HttpStatus.BAD_REQUEST),
    DELETE_OTHER_ADMIN_INVALID(1024, "Bạn không thể xóa tài khoản Quản trị viên khác!", HttpStatus.BAD_REQUEST),

    //reset password
    EMAIL_NOT_FOUND(1057, "Email không tồn tại trong hệ thống", HttpStatus.NOT_FOUND),
    OTP_REQUIRED(1058, "Mã OTP không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_OTP(1059, "Mã OTP phải có 6 chữ số", HttpStatus.BAD_REQUEST),
    OTP_NOT_FOUND(1060, "Mã OTP không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(1061, "Mã OTP đã hết hạn", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED(1062, "Mã OTP đã được sử dụng", HttpStatus.BAD_REQUEST),
    INVALID_OTP_OR_EMAIL(1063, "Mã OTP hoặc email không chính xác", HttpStatus.BAD_REQUEST),
    TOKEN_NOT_FOUND(1064, "Token không hợp lệ", HttpStatus.BAD_REQUEST),
    PASSWORDS_NOT_MATCH(1065, "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),
    CONFIRM_PASSWORD_REQUIRED(1066, "Vui lòng xác nhận mật khẩu", HttpStatus.BAD_REQUEST),

    PASSWORD_INCORRECT(1070, "Mật khẩu hiện tại không chính xác", HttpStatus.BAD_REQUEST),
    OTP_ATTEMPTS_EXCEEDED(1072, "Bạn đã nhập sai mã OTP quá số lần cho phép. Vui lòng yêu cầu mã mới.", HttpStatus.BAD_REQUEST),
    OTP_NOT_VERIFIED(1073, "Vui lòng xác thực mã OTP trước khi đặt lại mật khẩu", HttpStatus.BAD_REQUEST),

    WEAK_PASSWORD(1074, "Mật khẩu quá yếu. Cần tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.", HttpStatus.BAD_REQUEST),

    // ĐĂNG KÝ QUA OTP
    OTP_RESEND_TOO_SOON(1079, "Bạn vừa yêu cầu mã OTP. Vui lòng đợi 60 giây trước khi gửi lại.", HttpStatus.TOO_MANY_REQUESTS),
    REGISTRATION_SESSION_EXPIRED(1080, "Phiên đăng ký đã hết hạn. Vui lòng đăng ký lại.", HttpStatus.BAD_REQUEST),
    REGISTER_OTP_INCORRECT(1081, "Mã OTP không chính xác. Vui lòng kiểm tra lại.", HttpStatus.BAD_REQUEST),

    // TRUNG TÂM / RBAC
    CENTER_REQUIRED(1100, "Cần cung cấp trung tâm cho tài khoản quản lý", HttpStatus.BAD_REQUEST),
    INVALID_ROLE(1101, "Role không hợp lệ cho thao tác này", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(1102, "Role đã tồn tại", HttpStatus.CONFLICT),
    PERMISSION_NOT_FOUND(1103, "Permission không tồn tại", HttpStatus.NOT_FOUND),
    LAST_ADMIN(1104, "Không thể vô hiệu hóa quyền quản trị cuối cùng", HttpStatus.CONFLICT),
    AUTH_DEPENDENCY_UNAVAILABLE(1105, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_DELIVERY_FAILED(1106, "Không thể gửi email xác thực, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_PAGE(1107, "Phân trang không hợp lệ", HttpStatus.BAD_REQUEST),
    ROLE_CODE_REQUIRED(1108, "Mã role không được để trống", HttpStatus.BAD_REQUEST),
    ROLE_NAME_REQUIRED(1109, "Tên role không được để trống", HttpStatus.BAD_REQUEST),
    REFRESH_REQUIRED(1110, "Refresh token không được để trống", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(1111, "CSRF token bị thiếu hoặc không hợp lệ, hãy gọi GET /auth/csrf rồi gửi header X-XSRF-TOKEN", HttpStatus.FORBIDDEN),

    // API NỘI BỘ (service-to-service)
    USER_IDS_REQUIRED(1112, "Danh sách userId không được để trống", HttpStatus.BAD_REQUEST),
    USER_BATCH_TOO_LARGE(1113, "Danh sách userId vượt quá giới hạn 100 phần tử", HttpStatus.BAD_REQUEST),

    // SERVICE CLIENT (client-credentials)
    INVALID_CLIENT_CREDENTIALS(1114, "clientId hoặc clientSecret không đúng", HttpStatus.UNAUTHORIZED),
    CLIENT_ID_REQUIRED(1115, "clientId không được để trống", HttpStatus.BAD_REQUEST),
    CLIENT_SECRET_REQUIRED(1116, "clientSecret không được để trống", HttpStatus.BAD_REQUEST),
    SERVICE_CLIENT_DISABLED(1117, "Service client đã bị vô hiệu hóa", HttpStatus.UNAUTHORIZED),

    // PHIÊN & BẢO MẬT HỢP NHẤT
    LOGIN_ATTEMPTS_EXCEEDED(1082, "Bạn đã nhập sai mật khẩu quá 5 lần. Vui lòng thử lại sau 15 phút.", HttpStatus.TOO_MANY_REQUESTS),
    SESSION_NOT_FOUND(1083, "Phiên đăng nhập không tồn tại hoặc đã hết hạn", HttpStatus.NOT_FOUND),
    SESSION_ACCESS_DENIED(1084, "Bạn không có quyền thao tác trên phiên đăng nhập này", HttpStatus.FORBIDDEN),
    TOKEN_REUSED(1085, "Phát hiện token đã bị thu hồi được sử dụng lại. Toàn bộ phiên đã bị hủy vì lý do bảo mật.", HttpStatus.UNAUTHORIZED);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
