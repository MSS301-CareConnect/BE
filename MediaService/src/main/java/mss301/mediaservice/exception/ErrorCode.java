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
    MEDIA_ACCESS_DENIED(7202, "File does not exist or does not belong to this service", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(401, "Not authenticated or invalid internal secret", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(7000, "Invalid request data", HttpStatus.BAD_REQUEST),
    FILE_REQUIRED(7100, "Please select a file", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(7101, "File exceeds the allowed size", HttpStatus.PAYLOAD_TOO_LARGE),
    INVALID_FILE_TYPE(7102, "File type is not supported", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_CONTENT_MISMATCH(7103, "File content does not match the declared type", HttpStatus.BAD_REQUEST),
    INVALID_SOURCE_SERVICE(7104, "Invalid source service name", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(7200, "File not found", HttpStatus.NOT_FOUND),
    MEDIA_NOT_ACTIVE(7201, "File is no longer active", HttpStatus.CONFLICT),
    UPLOAD_FAILED(7300, "Could not upload the file to storage", HttpStatus.SERVICE_UNAVAILABLE),
    DOWNLOAD_URL_FAILED(7301, "Could not create a download URL", HttpStatus.SERVICE_UNAVAILABLE),
    DELETE_FILE_FAILED(7302, "Could not delete the file from storage", HttpStatus.SERVICE_UNAVAILABLE),
    DATABASE_UNAVAILABLE(7400, "MediaService database is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    UNCATEGORIZED_EXCEPTION(9999, "An unknown error occurred", HttpStatus.INTERNAL_SERVER_ERROR);

    int code;
    String message;
    HttpStatusCode statusCode;
}
