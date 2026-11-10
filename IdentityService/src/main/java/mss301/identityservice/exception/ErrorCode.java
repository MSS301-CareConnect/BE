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
    INVALID_REQUEST(1000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_KEY(1001, "Invalid message key", HttpStatus.BAD_REQUEST),
    LOCK_REASON_REQUIRED(1118, "A reason is required to lock the account", HttpStatus.BAD_REQUEST),
    INVALID_DATE_FILTER(1119, "Invalid date range or range exceeds 366 days", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    //USER ERRORS
    EMAIL_EXISTED(1003, "Email is already in use, please use another email", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1006, "Incorrect username or password", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(1007, "Incorrect username or password", HttpStatus.UNAUTHORIZED),
    PASSWORD_REQUIRED(1009, "Password is required", HttpStatus.BAD_REQUEST),
    FULLNAME_REQUIRED(1010, "Full name is required", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(1011, "Email is required", HttpStatus.BAD_REQUEST),
    PHONE_REQUIRED(1012, "Phone number is required", HttpStatus.BAD_REQUEST),
    ROLE_ID_REQUIRED(1013, "Role ID is required", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(1014, "Phone number must have 10-11 digits", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(1015, "Invalid email format (e.g. 112@gmail.com)", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1016, "User does not exist", HttpStatus.NOT_FOUND),
    INVALID_FULLNAME(1017, "Full name must not exceed 255 characters", HttpStatus.BAD_REQUEST),

    // ROLE ERRORS
    ROLE_NOT_FOUND(1019, "Role does not exist", HttpStatus.NOT_FOUND),
    DEFAULT_ROLE_NOT_FOUND(1020, "Default role not found", HttpStatus.NOT_FOUND),
    USER_INACTIVE(1021, "Your account has been locked. Please contact an administrator to unlock it.", HttpStatus.FORBIDDEN),

    DELETE_ADMIN_INVALID(1022, "Cannot delete a system administrator account", HttpStatus.BAD_REQUEST),
    DELETE_SELF_INVALID(1023, "You cannot lock your own account!", HttpStatus.BAD_REQUEST),
    DELETE_OTHER_ADMIN_INVALID(1024, "You cannot delete another administrator account!", HttpStatus.BAD_REQUEST),

    //reset password
    EMAIL_NOT_FOUND(1057, "Email does not exist in the system", HttpStatus.NOT_FOUND),
    OTP_REQUIRED(1058, "OTP code is required", HttpStatus.BAD_REQUEST),
    INVALID_OTP(1059, "OTP code must have 6 digits", HttpStatus.BAD_REQUEST),
    OTP_NOT_FOUND(1060, "OTP code is invalid or has expired", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED(1061, "OTP code has expired", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED(1062, "OTP code has already been used", HttpStatus.BAD_REQUEST),
    INVALID_OTP_OR_EMAIL(1063, "Incorrect OTP code or email", HttpStatus.BAD_REQUEST),
    TOKEN_NOT_FOUND(1064, "Invalid token", HttpStatus.BAD_REQUEST),
    PASSWORDS_NOT_MATCH(1065, "Password confirmation does not match", HttpStatus.BAD_REQUEST),
    CONFIRM_PASSWORD_REQUIRED(1066, "Please confirm your password", HttpStatus.BAD_REQUEST),

    PASSWORD_INCORRECT(1070, "Current password is incorrect", HttpStatus.BAD_REQUEST),
    OTP_ATTEMPTS_EXCEEDED(1072, "Too many incorrect OTP attempts. Please request a new code.", HttpStatus.BAD_REQUEST),
    OTP_NOT_VERIFIED(1073, "Please verify the OTP code before resetting your password", HttpStatus.BAD_REQUEST),

    WEAK_PASSWORD(1074, "Password is too weak. It must have at least 8 characters, including uppercase, lowercase, a digit and a special character.", HttpStatus.BAD_REQUEST),

    // ĐĂNG KÝ QUA OTP
    OTP_RESEND_TOO_SOON(1079, "You just requested an OTP code. Please wait 60 seconds before requesting again.", HttpStatus.TOO_MANY_REQUESTS),
    REGISTRATION_SESSION_EXPIRED(1080, "Registration session has expired. Please register again.", HttpStatus.BAD_REQUEST),
    REGISTER_OTP_INCORRECT(1081, "Incorrect OTP code. Please check and try again.", HttpStatus.BAD_REQUEST),

    // TRUNG TÂM / RBAC
    CENTER_REQUIRED(1100, "A center is required for a manager account", HttpStatus.BAD_REQUEST),
    INVALID_ROLE(1101, "Role is not valid for this operation", HttpStatus.BAD_REQUEST),
    ROLE_EXISTED(1102, "Role already exists", HttpStatus.CONFLICT),
    PERMISSION_NOT_FOUND(1103, "Permission does not exist", HttpStatus.NOT_FOUND),
    LAST_ADMIN(1104, "Cannot disable the last administrator", HttpStatus.CONFLICT),
    AUTH_DEPENDENCY_UNAVAILABLE(1105, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    EMAIL_DELIVERY_FAILED(1106, "Could not send the verification email, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_PAGE(1107, "Invalid pagination parameters", HttpStatus.BAD_REQUEST),
    ROLE_CODE_REQUIRED(1108, "Role code is required", HttpStatus.BAD_REQUEST),
    ROLE_NAME_REQUIRED(1109, "Role name is required", HttpStatus.BAD_REQUEST),
    REFRESH_REQUIRED(1110, "Refresh token is required", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(1111, "CSRF token is missing or invalid; call GET /auth/csrf and send the X-XSRF-TOKEN header", HttpStatus.FORBIDDEN),

    // API NỘI BỘ (service-to-service)
    USER_IDS_REQUIRED(1112, "userId list is required", HttpStatus.BAD_REQUEST),
    USER_BATCH_TOO_LARGE(1113, "userId list exceeds the limit of 100 items", HttpStatus.BAD_REQUEST),

    // SERVICE CLIENT (client-credentials)
    INVALID_CLIENT_CREDENTIALS(1114, "Incorrect clientId or clientSecret", HttpStatus.UNAUTHORIZED),
    CLIENT_ID_REQUIRED(1115, "clientId is required", HttpStatus.BAD_REQUEST),
    CLIENT_SECRET_REQUIRED(1116, "clientSecret is required", HttpStatus.BAD_REQUEST),
    SERVICE_CLIENT_DISABLED(1117, "Service client has been disabled", HttpStatus.UNAUTHORIZED),

    // PHIÊN & BẢO MẬT HỢP NHẤT
    LOGIN_ATTEMPTS_EXCEEDED(1082, "Too many incorrect password attempts (5). Please try again in 15 minutes.", HttpStatus.TOO_MANY_REQUESTS),
    SESSION_NOT_FOUND(1083, "Login session does not exist or has expired", HttpStatus.NOT_FOUND),
    SESSION_ACCESS_DENIED(1084, "You do not have permission to manage this login session", HttpStatus.FORBIDDEN),
    TOKEN_REUSED(1085, "A revoked token was reused. All sessions have been terminated for security reasons.", HttpStatus.UNAUTHORIZED);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
