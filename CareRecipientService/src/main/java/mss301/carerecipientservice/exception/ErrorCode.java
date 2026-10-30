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
    RECIPIENT_INACTIVE(2701, "Care recipient profile is suspended", HttpStatus.CONFLICT),
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(2000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_KEY(2001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(2002, "CSRF token is missing or invalid; call GET /auth/csrf and send the X-XSRF-TOKEN header", HttpStatus.FORBIDDEN),
    INVALID_PAGE(2003, "Invalid pagination parameters", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(2004, "Care recipient service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(2005, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(2006, "The data was just changed by someone else, please reload and try again", HttpStatus.CONFLICT),

    // HỒ SƠ NGƯỜI BỆNH
    RECIPIENT_NOT_FOUND(2100, "Care recipient profile does not exist", HttpStatus.NOT_FOUND),
    RECIPIENT_ACCESS_DENIED(2101, "You do not have sufficient permission to perform this action on the profile", HttpStatus.FORBIDDEN),
    RECIPIENT_VERSION_CONFLICT(2102, "The profile was updated by someone else, please reload", HttpStatus.CONFLICT),
    INVALID_STATUS(2103, "Status can only change between ACTIVE and INACTIVE", HttpStatus.BAD_REQUEST),
    FULLNAME_REQUIRED(2104, "Full name is required", HttpStatus.BAD_REQUEST),
    INVALID_FULLNAME(2105, "Full name must not exceed 255 characters", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(2106, "Phone number must have 10-11 digits", HttpStatus.BAD_REQUEST),
    INVALID_DATE_OF_BIRTH(2107, "Date of birth cannot be in the future", HttpStatus.BAD_REQUEST),
    INVALID_COORDINATES(2108, "Invalid coordinates: both latitude (-90..90) and longitude (-180..180) are required, with at most 6 decimal places", HttpStatus.BAD_REQUEST),
    INVALID_MEASUREMENT(2109, "Height / weight must be greater than 0 and reasonable", HttpStatus.BAD_REQUEST),
    TEXT_TOO_LONG(2110, "Content exceeds the allowed length", HttpStatus.BAD_REQUEST),
    INVALID_AVATAR_URL(2111, "Avatar must be a valid http(s) URL", HttpStatus.BAD_REQUEST),
    VERSION_REQUIRED(2112, "Profile version is required", HttpStatus.BAD_REQUEST),
    RELATIONSHIP_TYPE_REQUIRED(2113, "Relationship to the care recipient is required", HttpStatus.BAD_REQUEST),
    TOO_MANY_ITEMS(2114, "Number of items exceeds the limit", HttpStatus.BAD_REQUEST),

    // THUỐC
    MEDICATION_NOT_FOUND(2200, "Medication does not exist", HttpStatus.NOT_FOUND),
    MEDICATION_NAME_REQUIRED(2201, "Medication name is required", HttpStatus.BAD_REQUEST),
    START_DATE_REQUIRED(2202, "Medication start date is required", HttpStatus.BAD_REQUEST),
    INVALID_MEDICATION_DATES(2203, "End date must be on or after the start date", HttpStatus.BAD_REQUEST),
    INVALID_TIME_OF_DAY(2204, "Medication time must be in HH:mm format", HttpStatus.BAD_REQUEST),

    // LIÊN HỆ KHẨN CẤP
    CONTACT_NOT_FOUND(2300, "Emergency contact does not exist", HttpStatus.NOT_FOUND),
    CONTACT_NAME_REQUIRED(2301, "Emergency contact name is required", HttpStatus.BAD_REQUEST),
    CONTACT_PHONE_REQUIRED(2302, "Emergency contact phone number is required", HttpStatus.BAD_REQUEST),
    CONTACT_RELATIONSHIP_REQUIRED(2303, "Emergency contact relationship is required", HttpStatus.BAD_REQUEST),
    INVALID_PRIORITY(2304, "Priority must be between 0 and 100", HttpStatus.BAD_REQUEST),
    DUPLICATE_PRIMARY_CONTACT(2305, "Only one primary emergency contact is allowed", HttpStatus.BAD_REQUEST),

    // NGƯỜI THÂN QUẢN LÝ HỒ SƠ
    MANAGER_NOT_FOUND(2400, "Profile manager does not exist", HttpStatus.NOT_FOUND),
    MANAGER_USER_INVALID(2401, "Cannot add: email does not belong to an active customer account", HttpStatus.BAD_REQUEST),
    MANAGER_EXISTED(2402, "This person already manages the profile", HttpStatus.CONFLICT),
    LAST_OWNER(2403, "Cannot remove or downgrade the last OWNER", HttpStatus.CONFLICT),
    PRIMARY_MUST_BE_OWNER(2404, "The primary manager must have OWNER access", HttpStatus.BAD_REQUEST),
    PRIMARY_TRANSFER_REQUIRED(2405, "Transfer the primary manager role to someone else first", HttpStatus.CONFLICT),
    ACCESS_LEVEL_REQUIRED(2406, "Profile access level is required", HttpStatus.BAD_REQUEST),
    EMAIL_REQUIRED(2407, "Email is required", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(2408, "Invalid email format", HttpStatus.BAD_REQUEST),
    MANAGER_UPDATE_EMPTY(2409, "At least one change is required (accessLevel or relationshipType)", HttpStatus.BAD_REQUEST),
    INVITATION_NOT_FOUND(2410, "Invitation does not exist", HttpStatus.NOT_FOUND),
    INVITATION_NOT_PENDING(2411, "Invitation has already been processed", HttpStatus.CONFLICT),
    INVITATION_EXPIRED(2412, "Invitation has expired", HttpStatus.CONFLICT),
    INVITATION_EXISTED(2413, "A pending invitation already exists for this email", HttpStatus.CONFLICT),
    PRIMARY_OWNER_REQUIRED(2414, "Only the primary manager can invite or remove family members", HttpStatus.FORBIDDEN),
    RECIPIENT_HAS_ACTIVE_BOOKINGS(2415, "The care recipient still has unfinished bookings; cancel them or wait until they complete", HttpStatus.CONFLICT),

    // TÍCH HỢP IDENTITY
    IDENTITY_UNAVAILABLE(2500, "Account service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    IDENTITY_USER_NOT_FOUND(2501, "Account does not exist", HttpStatus.NOT_FOUND),

    // TÍCH HỢP BOOKING (cùng nhóm 25xx tích hợp)
    BOOKING_UNAVAILABLE(2502, "Booking service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),

    // API NỘI BỘ
    RECIPIENT_IDS_REQUIRED(2600, "recipientId list is required", HttpStatus.BAD_REQUEST),
    RECIPIENT_BATCH_TOO_LARGE(2601, "recipientId list exceeds the limit of 100 items", HttpStatus.BAD_REQUEST),

    // TOKEN SERVICE (client-credentials) — thiếu cấu hình / sai secret / Identity sập đều là lỗi hạ tầng với người dùng
    SERVICE_TOKEN_UNAVAILABLE(2700, "Could not authenticate with a dependent service, please try again later", HttpStatus.SERVICE_UNAVAILABLE);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
