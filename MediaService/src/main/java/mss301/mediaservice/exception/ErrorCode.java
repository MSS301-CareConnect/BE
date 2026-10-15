package mss301.mediaservice.exception;

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
    UNAUTHENTICATED(401, "Chưa xác thực hoặc internal secret không hợp lệ", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Đường dẫn không tồn tại", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(5000, "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    FILE_REQUIRED(5100, "Vui lòng chọn file", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(5101, "File vượt quá dung lượng cho phép", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_TYPE(5102, "Định dạng file không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_CONTENT_MISMATCH(5103, "Nội dung file không khớp định dạng đã khai báo", HttpStatus.BAD_REQUEST),
    INVALID_SOURCE_SERVICE(5104, "Tên service nguồn không hợp lệ", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(5200, "Không tìm thấy file", HttpStatus.NOT_FOUND),
    MEDIA_NOT_ACTIVE(5201, "File không còn hoạt động", HttpStatus.CONFLICT),
    UPLOAD_FAILED(5300, "Không thể tải file lên kho lưu trữ", HttpStatus.SERVICE_UNAVAILABLE),
    DOWNLOAD_URL_FAILED(5301, "Không thể tạo đường dẫn tải file", HttpStatus.SERVICE_UNAVAILABLE),
    DELETE_FILE_FAILED(5302, "Không thể xóa file khỏi kho lưu trữ", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_UNAVAILABLE(5400, "Cơ sở dữ liệu MediaService tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    UNCATEGORIZED_EXCEPTION(9999, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR);

    int code;
    String message;
    HttpStatusCode statusCode;
}
