package mss301.aiservice.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "Bạn chưa đăng nhập hoặc token không hợp lệ", HttpStatus.UNAUTHORIZED),

    UNAUTHORIZED(403, "Bạn không có quyền sử dụng chức năng này", HttpStatus.FORBIDDEN),

    PATH_NOT_FOUND(404, "Đường dẫn không tồn tại", HttpStatus.NOT_FOUND),


    INVALID_REQUEST(8000, "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),

    INVALID_KEY(8001, "Mã validation không hợp lệ", HttpStatus.BAD_REQUEST),

    CSRF_INVALID(8002, "CSRF token bị thiếu hoặc không hợp lệ", HttpStatus.FORBIDDEN),

    AI_SERVICE_UNAVAILABLE(8003, "Trợ lý AI tạm thời không hoạt động, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),

    AUTH_DEPENDENCY_UNAVAILABLE(8004, "Dịch vụ xác thực tạm thời không hoạt động", HttpStatus.SERVICE_UNAVAILABLE),

    CONCURRENT_UPDATE(8005, "Dữ liệu vừa được thay đổi, vui lòng thử lại", HttpStatus.CONFLICT),

    UNSUPPORTED_MEDIA_TYPE(8006, "Định dạng request không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),


    PROMPT_REQUIRED(8100, "Vui lòng nhập câu hỏi", HttpStatus.BAD_REQUEST),

    GUEST_PROMPT_TOO_LONG(8101, "Câu hỏi của khách vượt quá độ dài cho phép", HttpStatus.BAD_REQUEST),

    CUSTOMER_PROMPT_TOO_LONG(8102, "Câu hỏi vượt quá độ dài cho phép", HttpStatus.BAD_REQUEST),

    GUEST_IMAGE_NOT_ALLOWED(8103, "Vui lòng đăng nhập để gửi ảnh cho trợ lý AI", HttpStatus.FORBIDDEN),

    TOO_MANY_IMAGES(8104, "Mỗi câu hỏi chỉ được gửi tối đa 3 ảnh", HttpStatus.BAD_REQUEST),

    IMAGE_TOO_LARGE(8105, "Có ảnh vượt quá dung lượng cho phép", HttpStatus.BAD_REQUEST),

    TOTAL_IMAGE_SIZE_TOO_LARGE(8106, "Tổng dung lượng ảnh vượt quá giới hạn cho phép", HttpStatus.BAD_REQUEST),

    UNSUPPORTED_IMAGE_TYPE(8107, "Chỉ hỗ trợ ảnh JPG, JPEG, PNG hoặc WebP", HttpStatus.BAD_REQUEST),

    GUEST_DAILY_LIMIT_EXCEEDED(8108, "Bạn đã dùng hết lượt hỏi miễn phí hôm nay, vui lòng đăng nhập hoặc quay lại ngày mai", HttpStatus.TOO_MANY_REQUESTS),

    EMPTY_IMAGE(8109, "Ảnh tải lên không được để trống", HttpStatus.BAD_REQUEST),

    INVALID_IMAGE_NAME(8110, "Tên hoặc phần mở rộng của ảnh không hợp lệ", HttpStatus.BAD_REQUEST),

    // CONVERSATION: 8200-8299

    CONVERSATION_NOT_FOUND(8200, "Không tìm thấy cuộc trò chuyện", HttpStatus.NOT_FOUND),

    CONVERSATION_ACCESS_DENIED(8201, "Bạn không có quyền xem cuộc trò chuyện này", HttpStatus.FORBIDDEN),

    CONVERSATION_ARCHIVED(8202, "Cuộc trò chuyện đã được lưu trữ và không thể tiếp tục", HttpStatus.CONFLICT),

    INVALID_CONVERSATION_VERSION(8203, "Phiên bản cuộc trò chuyện không hợp lệ", HttpStatus.CONFLICT),

    CONVERSATION_TITLE_REQUIRED(8204, "Please enter a conversation name", HttpStatus.BAD_REQUEST),

    CONVERSATION_TITLE_TOO_LONG(8205, "Conversation name must be at most 120 characters", HttpStatus.BAD_REQUEST),

    // CERTIFICATE CHECK: 8600-8699 (chỉ ProviderService gọi)

    CERTIFICATE_HOLDER_NAME_REQUIRED(8600, "Certificate holder name is required", HttpStatus.BAD_REQUEST),

    CERTIFICATE_NAME_REQUIRED(8601, "Certificate name is required", HttpStatus.BAD_REQUEST),

    CERTIFICATE_IMAGE_URL_REQUIRED(8602, "Each certificate file needs a download URL", HttpStatus.BAD_REQUEST),

    TOO_MANY_CERTIFICATE_IMAGES(8603, "A certificate check accepts at most 10 files", HttpStatus.BAD_REQUEST),

    VISION_SERVICE_UNAVAILABLE(8604, "Dịch vụ đọc nội dung chứng chỉ hiện không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),


    // GROQ: 8300-8399

    GROQ_UNAVAILABLE(8300, "Không thể kết nối Groq, vui lòng thử lại sau", HttpStatus.SERVICE_UNAVAILABLE),

    GROQ_INVALID_RESPONSE(8301, "Groq trả về dữ liệu không hợp lệ", HttpStatus.BAD_GATEWAY),

    GROQ_REQUEST_REJECTED(8302, "Groq từ chối xử lý yêu cầu", HttpStatus.BAD_GATEWAY),

    // MEDIA: 8400-8499

    MEDIA_UPLOAD_FAILED(8400, "Không thể tải ảnh lên hệ thống, vui lòng thử lại", HttpStatus.SERVICE_UNAVAILABLE),

    MEDIA_SERVICE_UNAVAILABLE(8401, "Dịch vụ lưu trữ ảnh tạm thời không hoạt động", HttpStatus.SERVICE_UNAVAILABLE),

    SERVICE_TOKEN_UNAVAILABLE(8500, "AIService không thể xác thực với service phụ thuộc", HttpStatus.SERVICE_UNAVAILABLE),

    REMOTE_SERVICE_REJECTED(8501, "Service phụ thuộc từ chối yêu cầu của AIService", HttpStatus.SERVICE_UNAVAILABLE),

    GUEST_CONVERSATION_NOT_ALLOWED(8111, "Khách chưa đăng nhập không thể tiếp tục cuộc trò chuyện đã lưu", HttpStatus.UNAUTHORIZED),

    // Certificate verification (OCR + QR, CertificateVerificationController): 8610-8619 — 8600-8609 là của certificate-checks.
    CERTIFICATE_IMAGE_REQUIRED(8610, "Chứng chỉ phải có ít nhất một ảnh", HttpStatus.BAD_REQUEST),

    CERTIFICATE_IMAGE_LIMIT_EXCEEDED(8611, "Chứng chỉ chỉ được kiểm tra tối đa hai ảnh", HttpStatus.BAD_REQUEST),

    CERTIFICATE_IMAGE_INVALID(8612, "Không thể đọc ảnh chứng chỉ", HttpStatus.BAD_REQUEST),

    STROKE_MODEL_UNAVAILABLE(8613, "Stroke screening service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    int code;
    String message;
    HttpStatusCode statusCode;
}
