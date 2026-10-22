package mss301.bookingservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của BookingService: 5000–5999 (+ 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service).
// 50xx chung, 51xx booking, 52xx lịch/buổi, 54xx phân công/trung tâm, 55xx Identity, 56xx CareRecipient, 57xx Provider.
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa được xác thực", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền truy cập", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Không tìm thấy đường dẫn", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(5000, "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_KEY(5001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(5002, "CSRF token bị thiếu hoặc không hợp lệ, hãy gọi GET /auth/csrf rồi gửi header X-XSRF-TOKEN", HttpStatus.FORBIDDEN),
    INVALID_PAGE(5003, "Phân trang không hợp lệ", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(5004, "Dịch vụ đặt lịch tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(5005, "Dịch vụ xác thực tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(5006, "Dữ liệu vừa được thay đổi bởi người khác, vui lòng tải lại rồi thử lại", HttpStatus.CONFLICT),
    CENTER_SCOPE_REQUIRED(5007, "Tài khoản chưa được gắn với trung tâm chăm sóc", HttpStatus.FORBIDDEN),
    TEXT_TOO_LONG(5008, "Nội dung vượt quá độ dài cho phép", HttpStatus.BAD_REQUEST),

    // BOOKING
    BOOKING_NOT_FOUND(5100, "Booking không tồn tại", HttpStatus.NOT_FOUND),
    INVALID_BOOKING_TRANSITION(5101, "Không thể thực hiện thao tác này ở trạng thái hiện tại của booking", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(5102, "Idempotency-Key đã được dùng cho một yêu cầu đặt lịch khác", HttpStatus.CONFLICT),
    INVALID_IDEMPOTENCY_KEY(5103, "Idempotency-Key tối đa 100 ký tự chữ, số, '-', '_' hoặc '.'", HttpStatus.BAD_REQUEST),
    RECIPIENT_ID_REQUIRED(5104, "Hồ sơ người bệnh không được để trống", HttpStatus.BAD_REQUEST),
    CENTER_ID_REQUIRED(5105, "Trung tâm chăm sóc không được để trống", HttpStatus.BAD_REQUEST),
    REASON_REQUIRED(5106, "Lý do không được để trống", HttpStatus.BAD_REQUEST),
    BOOKING_ALREADY_STARTED(5107, "Đã tới giờ buổi chăm sóc đầu tiên, không thể hủy booking", HttpStatus.CONFLICT),
    SERVICES_REQUIRED(5108, "Cần chọn ít nhất một dịch vụ", HttpStatus.BAD_REQUEST),
    TOO_MANY_SERVICES(5109, "Một booking tối đa 10 dịch vụ", HttpStatus.BAD_REQUEST),
    DUPLICATE_SERVICE(5110, "Mỗi dịch vụ chỉ được chọn một lần", HttpStatus.BAD_REQUEST),
    INVALID_SORT(5112, "Chỉ sắp xếp theo createdAt hoặc startDate, chiều asc / desc", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_ID_REQUIRED(5111, "Dịch vụ chăm sóc không được để trống", HttpStatus.BAD_REQUEST),

    // LỊCH / BUỔI CHĂM SÓC
    SCHEDULE_TYPE_REQUIRED(5200, "Kiểu lịch không được để trống (ONE_TIME, DAILY, WEEKLY)", HttpStatus.BAD_REQUEST),
    START_DATE_REQUIRED(5201, "Ngày bắt đầu không được để trống", HttpStatus.BAD_REQUEST),
    START_TIME_REQUIRED(5202, "Giờ bắt đầu không được để trống", HttpStatus.BAD_REQUEST),
    END_TIME_REQUIRED(5203, "Giờ kết thúc không được để trống", HttpStatus.BAD_REQUEST),
    END_DATE_REQUIRED(5204, "Lịch hằng ngày / hằng tuần cần ngày kết thúc", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(5205, "Ngày kết thúc phải bằng hoặc sau ngày bắt đầu (lịch một lần: cùng ngày)", HttpStatus.BAD_REQUEST),
    START_DATE_IN_PAST(5206, "Ngày bắt đầu không được ở quá khứ", HttpStatus.BAD_REQUEST),
    OVERNIGHT_NOT_SUPPORTED(5207, "Giờ kết thúc phải sau giờ bắt đầu (chưa hỗ trợ ca qua đêm)", HttpStatus.BAD_REQUEST),
    DAYS_OF_WEEK_REQUIRED(5208, "Lịch hằng tuần cần chọn ít nhất một thứ trong tuần", HttpStatus.BAD_REQUEST),
    INVALID_DAYS_OF_WEEK(5209, "Thứ trong tuần phải từ 1 (Thứ Hai) đến 7 (Chủ Nhật), không trùng lặp", HttpStatus.BAD_REQUEST),
    DAYS_OF_WEEK_NOT_ALLOWED(5210, "Chỉ lịch hằng tuần mới chọn thứ trong tuần", HttpStatus.BAD_REQUEST),
    NO_OCCURRENCE_IN_RANGE(5211, "Khoảng ngày đã chọn không có buổi chăm sóc nào", HttpStatus.BAD_REQUEST),
    TOO_MANY_OCCURRENCES(5212, "Số buổi chăm sóc vượt quá giới hạn cho phép", HttpStatus.BAD_REQUEST),
    SCHEDULE_SPAN_TOO_LONG(5213, "Khoảng thời gian đặt lịch vượt quá giới hạn cho phép", HttpStatus.BAD_REQUEST),
    LEAD_TIME_TOO_SHORT(5214, "Buổi đầu tiên phải bắt đầu sau thời gian báo trước tối thiểu", HttpStatus.BAD_REQUEST),
    CAREGIVER_SCHEDULE_CONFLICT(5215, "Người chăm sóc đã có lịch trùng thời gian", HttpStatus.CONFLICT),
    RECIPIENT_SCHEDULE_CONFLICT(5216, "Người bệnh đã có lịch chăm sóc trùng thời gian", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_FOUND(5217, "Buổi chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    OCCURRENCE_NOT_MODIFIABLE(5218, "Chỉ đổi được buổi chăm sóc chưa bắt đầu", HttpStatus.CONFLICT),
    INVALID_DATE_FILTER(5219, "Khoảng ngày lọc không hợp lệ hoặc vượt quá giới hạn", HttpStatus.BAD_REQUEST),
    SESSION_WINDOW_NOT_OPEN(5222, "Chưa tới giờ check-in (sớm nhất 30 phút trước giờ bắt đầu) hoặc buổi đã kết thúc", HttpStatus.CONFLICT),
    OCCURRENCE_ID_REQUIRED(5227, "Mã buổi chăm sóc không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_OCCURRENCE_TRANSITION(5221, "Buổi chăm sóc không ở trạng thái cho phép thao tác này", HttpStatus.CONFLICT),
    BOOKING_NOT_CONFIRMED(5223, "Booking chưa được xác nhận (chưa đặt cọc) hoặc đã kết thúc", HttpStatus.CONFLICT),
    CAREGIVER_NOT_AVAILABLE(5226, "Người chăm sóc không có lịch làm việc (hoặc đang nghỉ) vào thời gian đã chọn", HttpStatus.CONFLICT),
    TOO_MANY_PENDING_BOOKINGS(5228, "Bạn đang có quá nhiều booking chờ xử lý, vui lòng chờ trung tâm duyệt hoặc hủy bớt", HttpStatus.CONFLICT),
    CAREGIVER_IDS_REQUIRED(5229, "Danh sách người chăm sóc không được để trống", HttpStatus.BAD_REQUEST),
    SLOTS_REQUIRED(5230, "Danh sách khoảng thời gian không được để trống", HttpStatus.BAD_REQUEST),
    INVALID_TIME_SLOT(5231, "Khoảng thời gian không hợp lệ (giờ kết thúc phải sau giờ bắt đầu)", HttpStatus.BAD_REQUEST),
    TOO_MANY_CAREGIVER_IDS(5232, "Tối đa 200 người chăm sóc mỗi lần kiểm", HttpStatus.BAD_REQUEST),
    TOO_MANY_SLOTS(5233, "Tối đa 60 khoảng thời gian mỗi lần kiểm", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_REQUIRED(5224, "Cần chọn bảng giá cho mỗi dịch vụ", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_UNIT_MISMATCH(5225, "Thời lượng mỗi buổi vượt quá thời lượng của gói giá theo ca đã chọn", HttpStatus.BAD_REQUEST),

    // PHÂN CÔNG / TRUNG TÂM
    CAREGIVER_REQUIRED(5400, "Cần phân công người chăm sóc trước khi chấp nhận booking", HttpStatus.CONFLICT),
    CAREGIVER_ID_REQUIRED(5401, "Người chăm sóc không được để trống", HttpStatus.BAD_REQUEST),
    CAREGIVER_NOT_FOUND(5402, "Người chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    CAREGIVER_NOT_IN_CENTER(5403, "Người chăm sóc không thuộc trung tâm của booking", HttpStatus.BAD_REQUEST),
    CAREGIVER_NOT_VERIFIED(5404, "Người chăm sóc chưa được xác minh", HttpStatus.CONFLICT),
    CAREGIVER_NOT_ACTIVE(5405, "Người chăm sóc hiện không hoạt động", HttpStatus.CONFLICT),
    CAREGIVER_SERVICE_NOT_SUPPORTED(5406, "Người chăm sóc không thực hiện được tất cả dịch vụ đã chọn", HttpStatus.BAD_REQUEST),
    CENTER_NOT_FOUND(5407, "Trung tâm chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    CENTER_NOT_ACTIVE(5408, "Trung tâm chăm sóc hiện không nhận đặt lịch", HttpStatus.CONFLICT),

    // TÍCH HỢP CARE CATALOG
    CUSTOMER_HAS_OUTSTANDING_DEBT(5157, "Bạn còn hóa đơn quá hạn chưa thanh toán, vui lòng thanh toán trước khi đặt lịch mới", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_CANCELLABLE(5150, "Có buổi chăm sóc đã bắt đầu, đã kết thúc hoặc đã hủy — không hủy được", HttpStatus.CONFLICT),
    CANCEL_SCOPE_NOT_ALLOWED(5151, "Booking chưa đặt cọc chỉ hủy được toàn bộ, không hủy từng buổi", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_IN_BOOKING(5153, "Buổi chăm sóc không thuộc booking này", HttpStatus.BAD_REQUEST),
    CANCELLATION_PREVIEW_EXPIRED(5159, "Số tiền hoàn đã thay đổi hoặc bản xem trước đã hết hạn, vui lòng xem lại", HttpStatus.CONFLICT),
    SYSTEM_CANCELLATION_REASON_REQUIRED(5160, "Lý do hủy hệ thống không được để trống (CENTER_LOCKED, CAREGIVER_DEACTIVATED)", HttpStatus.BAD_REQUEST),
    PAYMENT_SERVICE_UNAVAILABLE(5154, "Dịch vụ thanh toán tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    CARE_CATALOG_UNAVAILABLE(5300, "Dịch vụ danh mục / bảng giá tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    PRICE_PLAN_NOT_FOUND(5301, "Bảng giá dịch vụ không tồn tại hoặc không hợp lệ", HttpStatus.NOT_FOUND),
    CENTER_SERVICE_NOT_SUPPORTED(5302, "Trung tâm không cung cấp dịch vụ chăm sóc đã chọn", HttpStatus.BAD_REQUEST),

    // TÍCH HỢP IDENTITY
    SESSION_NOT_FOUND(5800, "Ca chăm sóc không tồn tại", HttpStatus.NOT_FOUND),
    SESSION_ALREADY_EXISTS(5801, "Buổi chăm sóc này đã được check-in", HttpStatus.CONFLICT),
    SESSION_NOT_IN_PROGRESS(5802, "Ca chăm sóc đã kết thúc", HttpStatus.CONFLICT),
    INVALID_COORDINATES(5803, "Tọa độ không hợp lệ", HttpStatus.BAD_REQUEST),
    TASKS_PENDING(5805, "Còn công việc chưa hoàn thành hoặc chưa ghi lý do bỏ qua", HttpStatus.CONFLICT),
    NO_SHOW_NOTE_REQUIRED(5812, "Cần mô tả tình huống khách vắng mặt (10–1000 ký tự)", HttpStatus.BAD_REQUEST),
    NO_SHOW_NOT_REPORTED(5813, "Buổi chăm sóc chưa có báo cáo khách vắng mặt", HttpStatus.CONFLICT),
    NO_SHOW_ALREADY_REPORTED(5814, "Buổi chăm sóc này đã được báo khách vắng mặt", HttpStatus.CONFLICT),
    NO_SHOW_DECISION_REQUIRED(5815, "Cần chọn xác nhận hoặc bác báo cáo khách vắng mặt", HttpStatus.BAD_REQUEST),
    NO_SHOW_WINDOW_INVALID(5817, "Chỉ báo khách vắng mặt từ giờ bắt đầu buổi tới 60 phút sau giờ kết thúc", HttpStatus.CONFLICT),
    IDENTITY_UNAVAILABLE(5500, "Dịch vụ tài khoản tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(5501, "Không xác thực được với dịch vụ liên quan, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),

    // TÍCH HỢP CARE RECIPIENT
    CARE_RECIPIENT_UNAVAILABLE(5600, "Dịch vụ hồ sơ người bệnh tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),
    RECIPIENT_NOT_FOUND(5601, "Hồ sơ người bệnh không tồn tại", HttpStatus.NOT_FOUND),
    RECIPIENT_NOT_ACTIVE(5602, "Hồ sơ người bệnh đang tạm ngưng, không thể đặt lịch", HttpStatus.CONFLICT),

    // TÍCH HỢP PROVIDER
    PROVIDER_UNAVAILABLE(5700, "Dịch vụ trung tâm / người chăm sóc tạm thời không khả dụng, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
