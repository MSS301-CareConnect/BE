package mss301.carerecipientservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của CareRecipientService: 2000–2999 (+ 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    RECIPIENT_INACTIVE(2701, "Hồ sơ người bệnh đang tạm ngưng", HttpStatus.CONFLICT),
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(2000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY(2001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(2002, "CSRF token bị thiếu hoặc không hợp lệ, hãy gọi GET /auth/csrf rồi gửi header X-XSRF-TOKEN", HttpStatus.FORBIDDEN),
    INVALID_PAGE(2003, "Phân trang không hợp lệ", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(2004, "Dịch vụ hồ sơ người bệnh tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(2005, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(2006, "Dữ liệu vừa được thay đổi bởi người khác, vui lòng tải lại rồi thử lại", HttpStatus.CONFLICT),

    // HỒ SƠ NGƯỜI BỆNH
    RECIPIENT_NOT_FOUND(2100, "Hồ sơ người bệnh không tồn tại", HttpStatus.NOT_FOUND),
    RECIPIENT_ACCESS_DENIED(2101, "Bạn không đủ quyền thực hiện thao tác này trên hồ sơ", HttpStatus.FORBIDDEN),
    RECIPIENT_VERSION_CONFLICT(2102, "Hồ sơ đã được người khác cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    INVALID_STATUS(2103, "Chỉ được chuyển trạng thái giữa ACTIVE và INACTIVE", HttpStatus.BAD_REQUEST),
    FULLNAME_REQUIRED(2104, "Họ tên không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_FULLNAME(2105, "Họ tên không được vượt quá 255 ký tự", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(2106, "Số điện thoại phải có 10-11 chữ số", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(2107, "Ngày sinh không được ở tương lai", HttpStatus.BAD_REQUEST),
    INVALID_COORDINATES(2108, "Tọa độ không hợp lệ: cần cả vĩ độ (-90..90) và kinh độ (-180..180), tối đa 6 chữ số thập phân", HttpStatus.BAD_REQUEST),
    INVALID_MEASUREMENT(2109, "Chiều cao / cân nặng phải lớn hơn 0 và hợp lý", HttpStatus.BAD_REQUEST),
    TEXT_TOO_LONG(2110, "Nội dung vượt quá độ dài cho phép", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(2111, "Ảnh đại diện phải là URL http(s) hợp lệ", HttpStatus.BAD_REQUEST),
    VERSION_REQUIRED(2112, "Thiếu version của hồ sơ", HttpStatus.BAD_REQUEST),
    RELATIONSHIP_TYPE_REQUIRED(2113, "Quan hệ với người bệnh không được để trống", HttpStatus.BAD_REQUEST),
    TOO_MANY_ITEMS(2114, "Số lượng phần tử vượt quá giới hạn", HttpStatus.BAD_REQUEST),

    // THUỐC
    MEDICATION_NOT_FOUND(2200, "Thuốc không tồn tại", HttpStatus.NOT_FOUND),
    MEDICATION_NAME_REQUIRED(2201, "Tên thuốc không được để trống", HttpStatus.BAD_REQUEST),
    START_DATE_REQUIRED(2202, "Ngày bắt đầu dùng thuốc không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_MEDICATION_DATES(2203, "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu", HttpStatus.BAD_REQUEST),
    INVALID_TIME_OF_DAY(2204, "Giờ uống thuốc phải có dạng HH:mm", HttpStatus.BAD_REQUEST),

    // LIÊN HỆ KHẨN CẤP
    CONTACT_NOT_FOUND(2300, "Liên hệ khẩn cấp không tồn tại", HttpStatus.NOT_FOUND),
    CONTACT_NAME_REQUIRED(2301, "Tên liên hệ khẩn cấp không được để trống", HttpStatus.BAD_REQUEST),
    CONTACT_PHONE_REQUIRED(2302, "Số điện thoại liên hệ khẩn cấp không được để trống", HttpStatus.BAD_REQUEST),
    CONTACT_RELATIONSHIP_REQUIRED(2303, "Quan hệ của liên hệ khẩn cấp không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_PRIORITY(2304, "Thứ tự ưu tiên phải từ 0 đến 100", HttpStatus.BAD_REQUEST),
    DUPLICATE_PRIMARY_CONTACT(2305, "Chỉ được có một liên hệ khẩn cấp chính", HttpStatus.BAD_REQUEST),

    // NGƯỜI THÂN QUẢN LÝ HỒ SƠ
    MANAGER_NOT_FOUND(2400, "Người quản lý hồ sơ không tồn tại", HttpStatus.NOT_FOUND),
    MANAGER_USER_INVALID(2401, "Không thể thêm: email không thuộc tài khoản khách hàng đang hoạt động", HttpStatus.BAD_REQUEST),
    MANAGER_EXISTED(2402, "Người này đã quản lý hồ sơ", HttpStatus.CONFLICT),
    LAST_OWNER(2403, "Không thể gỡ hoặc hạ quyền chủ sở hữu (OWNER) cuối cùng", HttpStatus.CONFLICT),
    PRIMARY_MUST_BE_OWNER(2404, "Người quản lý chính phải có quyền OWNER", HttpStatus.BAD_REQUEST),
    PRIMARY_TRANSFER_REQUIRED(2405, "Hãy chuyển quyền quản lý chính cho người khác trước", HttpStatus.CONFLICT),
    ACCESS_LEVEL_REQUIRED(2406, "Quyền trên hồ sơ không được để trống", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(2407, "Email không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(2408, "Email không đúng định dạng", HttpStatus.BAD_REQUEST),
    MANAGER_UPDATE_EMPTY(2409, "Cần ít nhất một thay đổi (accessLevel hoặc relationshipType)", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_FOUND(2410, "Lời mời không tồn tại", HttpStatus.NOT_FOUND),
    INVITATION_NOT_PENDING(2411, "Lời mời đã được xử lý", HttpStatus.CONFLICT),
    INVITATION_EXPIRED(2412, "Lời mời đã hết hạn", HttpStatus.CONFLICT),
    INVITATION_EXISTED(2413, "Đã có lời mời đang chờ cho email này", HttpStatus.CONFLICT),
    PRIMARY_OWNER_REQUIRED(2414, "Chỉ người quản lý chính được mời hoặc gỡ người thân", HttpStatus.FORBIDDEN),
    RECIPIENT_HAS_ACTIVE_BOOKINGS(2415, "Người bệnh còn lịch chăm sóc chưa kết thúc, hãy hủy hoặc chờ hoàn tất trước", HttpStatus.CONFLICT),

    // TÍCH HỢP IDENTITY
    IDENTITY_UNAVAILABLE(2500, "Dịch vụ tài khoản tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    IDENTITY_USER_NOT_FOUND(2501, "Tài khoản không tồn tại", HttpStatus.NOT_FOUND),

    // TÍCH HỢP BOOKING (cùng nhóm 25xx tích hợp)
    BOOKING_UNAVAILABLE(2502, "Dịch vụ đặt lịch tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),

    // API NỘI BỘ
    RECIPIENT_IDS_REQUIRED(2600, "Danh sách recipientId không được để trống", HttpStatus.BAD_REQUEST),
    RECIPIENT_BATCH_TOO_LARGE(2601, "Danh sách recipientId vượt quá giới hạn 100 phần tử", HttpStatus.BAD_REQUEST),

    // TOKEN SERVICE (client-credentials) — thiếu cấu hình / sai secret / Identity sập đều là lỗi hạ tầng với người dùng
    SERVICE_TOKEN_UNAVAILABLE(2700, "Không xác thực được với dịch vụ liên quan, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
