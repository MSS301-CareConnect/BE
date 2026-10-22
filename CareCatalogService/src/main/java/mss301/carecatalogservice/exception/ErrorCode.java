package mss301.carecatalogservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của CareCatalogService: 3000–3999 (+ 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(3000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY(3001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(3002, "CSRF token bị thiếu hoặc không hợp lệ, hãy gọi GET /auth/csrf rồi gửi header X-XSRF-TOKEN", HttpStatus.FORBIDDEN),
    INVALID_PAGE(3003, "Phân trang không hợp lệ", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(3004, "Dịch vụ danh mục tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(3005, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(3006, "Dữ liệu vừa được thay đổi bởi người khác, vui lòng tải lại rồi thử lại", HttpStatus.CONFLICT),

    // CARE SERVICES (Danh mục chuẩn)
    CARE_SERVICE_NOT_FOUND(3100, "Dịch vụ chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    CARE_SERVICE_NAME_REQUIRED(3101, "Tên dịch vụ không được để trống", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_CATEGORY_REQUIRED(3102, "Danh mục không được để trống", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_STATUS_REQUIRED(3103, "Trạng thái không được để trống", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_ID_REQUIRED(3104, "Mã dịch vụ chăm sóc không được để trống", HttpStatus.BAD_REQUEST),
    SERVICE_NOT_ACTIVE(3105, "Dịch vụ chăm sóc hiện không hoạt động", HttpStatus.CONFLICT),
    SERVICES_REQUIRED(3106, "Cần ít nhất một dịch vụ", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_IN_USE(3110, "Dịch vụ chuẩn đang được trung tâm sử dụng — hãy chuyển trạng thái INACTIVE thay vì xóa", HttpStatus.CONFLICT),

    // CENTER SERVICES (Dịch vụ trung tâm)
    CENTER_SERVICE_NOT_FOUND(3200, "Dịch vụ trung tâm không tồn tại", HttpStatus.NOT_FOUND),
    CENTER_ID_REQUIRED(3201, "Center ID không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_SERVICE_ID_REQUIRED(3202, "Mã dịch vụ trung tâm không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_ACCESS_DENIED(3204, "Bạn không có quyền thao tác trên dịch vụ của trung tâm này", HttpStatus.FORBIDDEN),
    CENTER_SERVICE_ALREADY_EXISTS(3205, "Trung tâm đã đăng ký dịch vụ chăm sóc này", HttpStatus.CONFLICT),
    CENTER_SERVICE_IN_USE(3206, "Dịch vụ trung tâm còn bảng giá đang áp dụng — hãy chuyển trạng thái INACTIVE thay vì xóa", HttpStatus.CONFLICT),
    PRICE_RANGE_INVALID(3207, "Khoảng giá không hợp lệ (giá tối thiểu phải ≤ giá tối đa, không âm)", HttpStatus.BAD_REQUEST),

    // PRICE PLANS
    PRICE_PLAN_NOT_FOUND(3300, "Bảng giá không tồn tại", HttpStatus.NOT_FOUND),
    PRICE_PLAN_NAME_REQUIRED(3301, "Tên bảng giá không được để trống", HttpStatus.BAD_REQUEST),
    PRICING_UNIT_REQUIRED(3302, "Đơn vị giá không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_DURATION(3303, "Thời lượng (phút) phải lớn hơn 0; bảng giá theo giờ (HOUR) phải là 60 phút", HttpStatus.BAD_REQUEST),
    INVALID_BASE_PRICE(3304, "Giá cơ bản phải lớn hơn hoặc bằng 0", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_ID_REQUIRED(3305, "Cần chọn bảng giá cho mỗi dịch vụ", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_NOT_ACTIVE(3306, "Bảng giá hiện không áp dụng", HttpStatus.CONFLICT);

    private int code;
    private String message;
    private HttpStatusCode statusCode;
}
