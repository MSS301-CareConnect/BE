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
    MEDIA_ACCESS_DENIED(7202, "File không tồn tại hoặc không thuộc dịch vụ này", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(401, "Chưa xác thực hoặc internal secret không hợp lệ", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Đường dẫn không tồn tại", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(7000, "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    FILE_REQUIRED(7100, "Vui lòng chọn file", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(7101, "File vượt quá dung lượng cho phép", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_TYPE(7102, "Định dạng file không được hỗ trợ", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_CONTENT_MISMATCH(7103, "Nội dung file không khớp định dạng đã khai báo", HttpStatus.BAD_REQUEST),
    INVALID_SOURCE_SERVICE(7104, "Tên service nguồn không hợp lệ", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(7200, "Không tìm thấy file", HttpStatus.NOT_FOUND),
    MEDIA_NOT_ACTIVE(7201, "File không còn hoạt động", HttpStatus.CONFLICT),
    UPLOAD_FAILED(7300, "Không thể tải file lên kho lưu trữ", HttpStatus.SERVICE_UNAVAILABLE),
    DOWNLOAD_URL_FAILED(7301, "Không thể tạo đường dẫn tải file", HttpStatus.SERVICE_UNAVAILABLE),
    DELETE_FILE_FAILED(7302, "Không thể xóa file khỏi kho lưu trữ", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_UNAVAILABLE(7400, "Cơ sở dữ liệu MediaService tạm thời không khả dụng", HttpStatus.SERVICE_UNAVAILABLE),
    UNCATEGORIZED_EXCEPTION(9999, "Đã xảy ra lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR);

    int code;
    String message;
    HttpStatusCode statusCode;
}
