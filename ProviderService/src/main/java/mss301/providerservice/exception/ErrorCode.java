package mss301.providerservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã ProviderService: 4000–4999 (+ 401, 403, 404, 9999 dùng chung giữa các service).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(4000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY(4001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(4002, "CSRF token bị thiếu hoặc không hợp lệ, hãy lấy token mới từ IdentityService", HttpStatus.FORBIDDEN),
    INVALID_PAGE(4003, "Phân trang không hợp lệ", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(4004, "Dịch vụ nhà cung cấp tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(4005, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(4006, "Dữ liệu vừa được thay đổi bởi người khác, vui lòng tải lại rồi thử lại", HttpStatus.CONFLICT),

    CENTER_INVALID_TRANSITION(4122, "Không thể chuyển trạng thái trung tâm", HttpStatus.CONFLICT),
    CENTER_MANAGER_REQUIRED(4120, "Trung tâm phải có manager đang hoạt động", HttpStatus.CONFLICT),
    CENTER_HAS_ACTIVE_BOOKINGS(4121, "Trung tâm vẫn còn lịch chăm sóc chưa kết thúc", HttpStatus.CONFLICT),
    CENTER_NOT_FOUND(4100, "Trung tâm chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    CENTER_EMAIL_EXISTED(4101, "Email trung tâm đã tồn tại", HttpStatus.CONFLICT),
    CENTER_NAME_REQUIRED(4102, "Tên trung tâm không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_NAME_TOO_LONG(4103, "Tên trung tâm không được vượt quá 255 ký tự", HttpStatus.BAD_REQUEST),
    CENTER_EMAIL_REQUIRED(4104, "Email trung tâm không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(4105, "Email trung tâm không đúng định dạng", HttpStatus.BAD_REQUEST),
    CENTER_ADDRESS_REQUIRED(4106, "Địa chỉ trung tâm không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_ADDRESS_TOO_LONG(4107, "Địa chỉ trung tâm không được vượt quá 2000 ký tự", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(4108, "Số điện thoại phải có từ 10 đến 15 chữ số", HttpStatus.BAD_REQUEST),
    OPERATING_HOURS_TOO_LONG(4109, "Giờ hoạt động không được vượt quá 500 ký tự", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REQUIRED(4110, "Trạng thái trung tâm không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REASON_REQUIRED(4111, "Phải nhập lý do khi từ chối hoặc đình chỉ trung tâm", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REASON_TOO_LONG(4112, "Lý do trạng thái không được vượt quá 500 ký tự", HttpStatus.BAD_REQUEST),
    CENTER_VERSION_REQUIRED(4113, "Thiếu version của trung tâm", HttpStatus.BAD_REQUEST),
    CENTER_VERSION_CONFLICT(4114, "Trung tâm đã được người khác cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    CENTER_ARCHIVED(4115, "Trung tâm đã được lưu trữ và không thể cập nhật", HttpStatus.CONFLICT),

    CENTER_NOT_ACTIVE(4116, "Trung tâm chưa hoạt động", HttpStatus.CONFLICT),
    CENTER_ACCESS_DENIED(4117, "Bạn không quản lý trung tâm này", HttpStatus.FORBIDDEN),
    CENTER_MANAGER_EXISTED(4118, "Tài khoản quản lý đã được gán cho trung tâm khác", HttpStatus.CONFLICT),
    PRICE_RANGE_INVALID(4119, "Giá tối thiểu không được lớn hơn giá tối đa", HttpStatus.BAD_REQUEST),

    CAREGIVER_INVALID_TRANSITION(4216, "Không thể chuyển trạng thái hồ sơ người chăm sóc", HttpStatus.CONFLICT),
    CAREGIVER_NOT_FOUND(4200, "Hồ sơ người chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    CAREGIVER_USER_EXISTED(4201, "Tài khoản đã có hồ sơ người chăm sóc", HttpStatus.CONFLICT),
    CAREGIVER_NOT_VERIFIED(4202, "Hồ sơ người chăm sóc chưa được xác minh", HttpStatus.CONFLICT),
    CAREGIVER_NOT_ACTIVE(4203, "Hồ sơ người chăm sóc không hoạt động", HttpStatus.CONFLICT),
    CAREGIVER_VERSION_CONFLICT(4204, "Hồ sơ người chăm sóc đã được cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    VERIFICATION_REASON_REQUIRED(4205, "Phải nhập lý do khi từ chối xác minh", HttpStatus.BAD_REQUEST),
    CAREGIVER_ACCESS_DENIED(4206, "Bạn không có quyền thao tác hồ sơ người chăm sóc này", HttpStatus.FORBIDDEN),
    CAREGIVER_SERVICE_NOT_IN_CENTER(4207, "Dịch vụ không thuộc trung tâm của người chăm sóc", HttpStatus.BAD_REQUEST),
    CAREGIVER_SERVICE_NOT_ACTIVE(4208, "Dịch vụ của trung tâm không tồn tại hoặc đang ngừng cung cấp", HttpStatus.CONFLICT),
    TOO_MANY_CAREGIVER_SERVICES(4209, "Mỗi người chăm sóc được gán tối đa 50 dịch vụ", HttpStatus.BAD_REQUEST),
    CAREGIVER_SERVICES_REQUIRED(4210, "Danh sách dịch vụ không được để trống (gửi [] để bỏ hết)", HttpStatus.BAD_REQUEST),
    SEARCH_TOO_BROAD(4211, "Có quá nhiều người chăm sóc phù hợp, vui lòng thu hẹp điều kiện tìm kiếm", HttpStatus.BAD_REQUEST),
    // Spec ghi 4210 nhưng 4210 đã thuộc CAREGIVER_SERVICES_REQUIRED (PRV-01) → dùng 4212.
    CARE_SERVICE_IDS_REQUIRED(4212, "Phải chọn từ 1 đến 10 dịch vụ cần tìm", HttpStatus.BAD_REQUEST),
    SEARCH_CENTER_REQUIRED(4213, "Phải chọn trung tâm khi tìm người chăm sóc", HttpStatus.BAD_REQUEST),
    MIN_RATING_INVALID(4214, "Điểm đánh giá tối thiểu phải từ 0 đến 5", HttpStatus.BAD_REQUEST),
    MIN_EXPERIENCE_INVALID(4215, "Số năm kinh nghiệm tối thiểu không được âm", HttpStatus.BAD_REQUEST),

    CERTIFICATE_INVALID_TRANSITION(4253, "Không thể chuyển bước xác minh chứng chỉ", HttpStatus.CONFLICT),
    CERTIFICATE_EXPIRED(4254, "Chứng chỉ đã hết hạn", HttpStatus.CONFLICT),
    CERTIFICATE_NOT_FOUND(4250, "Chứng chỉ không tồn tại", HttpStatus.NOT_FOUND),
    CERTIFICATE_VERSION_CONFLICT(4251, "Chứng chỉ đã được cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    CERTIFICATE_DATE_INVALID(4252, "Ngày hết hạn phải bằng hoặc sau ngày cấp", HttpStatus.BAD_REQUEST),

    WORK_SHIFT_NOT_FOUND(4300, "Ca làm việc không tồn tại", HttpStatus.NOT_FOUND),
    WORK_SHIFT_VERSION_CONFLICT(4301, "Ca làm việc đã được cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    WORK_SHIFT_TIME_INVALID(4302, "Giờ kết thúc phải sau giờ bắt đầu", HttpStatus.BAD_REQUEST),
    WORK_SHIFT_DATE_INVALID(4303, "Ngày làm việc hoặc hạn đăng ký không hợp lệ", HttpStatus.BAD_REQUEST),
    WORK_SHIFT_NOT_OPEN(4304, "Ca làm việc hiện không mở đăng ký", HttpStatus.CONFLICT),
    WORK_SHIFT_FULL(4305, "Ca làm việc đã đủ người", HttpStatus.CONFLICT),
    WORK_SHIFT_HAS_APPROVED_REGISTRATIONS(4306, "Không thể sửa hoặc hủy ca đã có đăng ký được duyệt", HttpStatus.CONFLICT),
    WORK_SHIFT_STATUS_INVALID(4307, "Không thể chuyển ca làm việc sang trạng thái yêu cầu", HttpStatus.CONFLICT),
    WORK_SHIFT_ALREADY_STARTED(4308, "Ca làm việc đã bắt đầu", HttpStatus.CONFLICT),

    REGISTRATION_NOT_FOUND(4350, "Đăng ký ca làm việc không tồn tại", HttpStatus.NOT_FOUND),
    REGISTRATION_EXISTED(4351, "Bạn đã đăng ký ca làm việc này", HttpStatus.CONFLICT),
    REGISTRATION_NOT_PENDING(4352, "Chỉ đăng ký đang chờ duyệt mới được xử lý", HttpStatus.CONFLICT),
    REGISTRATION_REJECTION_REASON_REQUIRED(4353, "Phải nhập lý do khi từ chối đăng ký", HttpStatus.BAD_REQUEST),
    REGISTRATION_DEADLINE_PASSED(4354, "Đã quá hạn đăng ký ca làm việc", HttpStatus.CONFLICT),

    SCHEDULE_NOT_FOUND(4400, "Lịch người chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    SCHEDULE_CONFLICT(4401, "Người chăm sóc đã có lịch trùng thời gian", HttpStatus.CONFLICT),
    SCHEDULE_STATUS_INVALID(4402, "Không thể chuyển lịch sang trạng thái yêu cầu", HttpStatus.CONFLICT),
    SCHEDULE_CANCELLATION_REASON_REQUIRED(4403, "Phải nhập lý do khi hủy lịch", HttpStatus.BAD_REQUEST),
    SCHEDULE_VERSION_CONFLICT(4404, "Lịch đã được cập nhật, vui lòng tải lại", HttpStatus.CONFLICT),
    TIME_OFF_NOT_FOUND(4405, "Ngày nghỉ không tồn tại", HttpStatus.NOT_FOUND),
    TIME_OFF_OVERLAP(4406, "Ngày nghỉ bị trùng với ngày nghỉ khác của người chăm sóc", HttpStatus.CONFLICT),
    TIME_OFF_RANGE_INVALID(4407, "Thời gian nghỉ không hợp lệ (kết thúc phải sau bắt đầu, tối đa 30 ngày)", HttpStatus.BAD_REQUEST),
    TIME_OFF_HAS_BOOKINGS(4408, "Khoảng nghỉ trùng buổi chăm sóc đã được giao, hãy nhờ quản lý đổi người trước", HttpStatus.CONFLICT),
    AVAILABILITY_RANGE_TOO_LONG(4409, "Khoảng thời gian tra cứu không hợp lệ hoặc vượt quá 31 ngày", HttpStatus.BAD_REQUEST),
    TIME_OFF_TIME_REQUIRED(4410, "Phải nhập thời điểm bắt đầu và kết thúc nghỉ", HttpStatus.BAD_REQUEST),
    TIME_OFF_REASON_TOO_LONG(4411, "Lý do nghỉ không được vượt quá 500 ký tự", HttpStatus.BAD_REQUEST),
    SLOTS_INVALID(4412, "Danh sách khung giờ phải có từ 1 đến 60 khung", HttpStatus.BAD_REQUEST),
    SLOT_RANGE_INVALID(4413, "Khung giờ không hợp lệ (thiếu thời điểm hoặc kết thúc không sau bắt đầu)", HttpStatus.BAD_REQUEST),
    CAREGIVER_ID_REQUIRED(4414, "Thiếu mã người chăm sóc", HttpStatus.BAD_REQUEST),
    SCHEDULE_HAS_BOOKINGS(4415, "Lịch đã có buổi chăm sóc được giao, không thể hủy", HttpStatus.CONFLICT),

    IDENTITY_UNAVAILABLE(4500, "Dịch vụ tài khoản tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    IDENTITY_USER_NOT_FOUND(4501, "Tài khoản không tồn tại", HttpStatus.NOT_FOUND),
    IDENTITY_USER_INVALID(4502, "Tài khoản không hoạt động hoặc không có đúng vai trò yêu cầu", HttpStatus.CONFLICT),

    MEDIA_REQUIRED(4600, "Phải chọn ít nhất một file", HttpStatus.BAD_REQUEST),
    MEDIA_LIMIT_EXCEEDED(4601, "Mỗi lần chỉ được tải tối đa 10 file", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(4602, "File không tồn tại hoặc không thuộc dữ liệu này", HttpStatus.NOT_FOUND),
    MEDIA_SERVICE_UNAVAILABLE(4603, "Dịch vụ file tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),

    REMOTE_COMMAND_REJECTED(4703, "Dịch vụ liên quan từ chối lệnh, cần kiểm tra cấu hình hoặc dữ liệu", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(4700, "Không xác thực được với dịch vụ liên quan, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),
    CATALOG_UNAVAILABLE(4701, "Dịch vụ danh mục tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    // Spec ghi 4700 nhưng 4700 đã là SERVICE_TOKEN_UNAVAILABLE (PRV-01) → dùng 4702.
    BOOKING_SERVICE_UNAVAILABLE(4702, "Dịch vụ đặt lịch tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE);
    private int code;
    private String message;
    private HttpStatusCode statusCode;
}
