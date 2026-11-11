package mss301.aiservice.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AppException extends RuntimeException {
    ErrorCode errorCode;

    // Tạo exception nghiệp vụ mang ErrorCode và message tương ứng.
    public AppException(ErrorCode errorCode) {
        super(errorCode == null ? null : errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
