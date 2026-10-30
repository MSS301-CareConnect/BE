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
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    INVALID_REQUEST(4000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_KEY(4001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(4002, "CSRF token is missing or invalid; get a new token from IdentityService", HttpStatus.FORBIDDEN),
    INVALID_PAGE(4003, "Invalid pagination parameters", HttpStatus.BAD_REQUEST),
    OUTBOX_NOT_FOUND(4800, "Outbox event does not exist", HttpStatus.NOT_FOUND),
    OUTBOX_NOT_FAILED(4801, "Only FAILED outbox events can be requeued / skipped", HttpStatus.CONFLICT),
    SERVICE_UNAVAILABLE(4004, "Provider service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(4005, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(4006, "The data was just changed by someone else, please reload and try again", HttpStatus.CONFLICT),

    CENTER_INVALID_TRANSITION(4122, "Cannot change the center status", HttpStatus.CONFLICT),
    CENTER_MANAGER_REQUIRED(4120, "The center must have an active manager", HttpStatus.CONFLICT),
    CENTER_HAS_ACTIVE_BOOKINGS(4121, "The center still has unfinished bookings", HttpStatus.CONFLICT),
    CENTER_NOT_FOUND(4100, "Care center does not exist", HttpStatus.NOT_FOUND),
    CENTER_EMAIL_EXISTED(4101, "Center email already exists", HttpStatus.CONFLICT),
    CENTER_NAME_REQUIRED(4102, "Center name is required", HttpStatus.BAD_REQUEST),
    CENTER_NAME_TOO_LONG(4103, "Center name must not exceed 255 characters", HttpStatus.BAD_REQUEST),
    CENTER_EMAIL_REQUIRED(4104, "Center email is required", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT(4105, "Invalid center email format", HttpStatus.BAD_REQUEST),
    CENTER_ADDRESS_REQUIRED(4106, "Center address is required", HttpStatus.BAD_REQUEST),
    CENTER_ADDRESS_TOO_LONG(4107, "Center address must not exceed 2000 characters", HttpStatus.BAD_REQUEST),
    INVALID_PHONE(4108, "Phone number must have 10 to 15 digits", HttpStatus.BAD_REQUEST),
    OPERATING_HOURS_TOO_LONG(4109, "Operating hours must not exceed 500 characters", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REQUIRED(4110, "Center status is required", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REASON_REQUIRED(4111, "A reason is required when rejecting or suspending a center", HttpStatus.BAD_REQUEST),
    CENTER_STATUS_REASON_TOO_LONG(4112, "Status reason must not exceed 500 characters", HttpStatus.BAD_REQUEST),
    CENTER_VERSION_REQUIRED(4113, "Center version is required", HttpStatus.BAD_REQUEST),
    CENTER_VERSION_CONFLICT(4114, "The center was updated by someone else, please reload", HttpStatus.CONFLICT),
    CENTER_ARCHIVED(4115, "The center is archived and cannot be updated", HttpStatus.CONFLICT),

    CENTER_NOT_ACTIVE(4116, "The center is not active", HttpStatus.CONFLICT),
    CENTER_ACCESS_DENIED(4117, "You do not manage this center", HttpStatus.FORBIDDEN),
    CENTER_MANAGER_EXISTED(4118, "The manager account is already assigned to another center", HttpStatus.CONFLICT),
    PRICE_RANGE_INVALID(4119, "Minimum price must not be greater than maximum price", HttpStatus.BAD_REQUEST),

    CAREGIVER_INVALID_TRANSITION(4216, "Cannot change the caregiver profile status", HttpStatus.CONFLICT),
    CAREGIVER_NOT_FOUND(4200, "Caregiver profile does not exist", HttpStatus.NOT_FOUND),
    CAREGIVER_USER_EXISTED(4201, "The account already has a caregiver profile", HttpStatus.CONFLICT),
    CAREGIVER_NOT_VERIFIED(4202, "Caregiver profile is not verified", HttpStatus.CONFLICT),
    CAREGIVER_NOT_ACTIVE(4203, "Caregiver profile is not active", HttpStatus.CONFLICT),
    CAREGIVER_VERSION_CONFLICT(4204, "The caregiver profile was updated, please reload", HttpStatus.CONFLICT),
    VERIFICATION_REASON_REQUIRED(4205, "A reason is required when rejecting verification", HttpStatus.BAD_REQUEST),
    CAREGIVER_ACCESS_DENIED(4206, "You do not have permission to manage this caregiver profile", HttpStatus.FORBIDDEN),
    CAREGIVER_SERVICE_NOT_IN_CENTER(4207, "The service does not belong to the caregiver's center", HttpStatus.BAD_REQUEST),
    CAREGIVER_SERVICE_NOT_ACTIVE(4208, "The center service does not exist or is no longer offered", HttpStatus.CONFLICT),
    TOO_MANY_CAREGIVER_SERVICES(4209, "Each caregiver can be assigned at most 50 services", HttpStatus.BAD_REQUEST),
    CAREGIVER_SERVICES_REQUIRED(4210, "Service list is required (send [] to remove all)", HttpStatus.BAD_REQUEST),
    SEARCH_TOO_BROAD(4211, "Too many matching caregivers, please narrow the search criteria", HttpStatus.BAD_REQUEST),
    // Spec ghi 4210 nhưng 4210 đã thuộc CAREGIVER_SERVICES_REQUIRED (PRV-01) → dùng 4212.
    CARE_SERVICE_IDS_REQUIRED(4212, "Select between 1 and 10 services to search for", HttpStatus.BAD_REQUEST),
    SEARCH_CENTER_REQUIRED(4213, "A center must be selected when searching for caregivers", HttpStatus.BAD_REQUEST),
    MIN_RATING_INVALID(4214, "Minimum rating must be between 0 and 5", HttpStatus.BAD_REQUEST),
    MIN_EXPERIENCE_INVALID(4215, "Minimum years of experience must not be negative", HttpStatus.BAD_REQUEST),

    CERTIFICATE_INVALID_TRANSITION(4253, "Cannot change the certificate verification step", HttpStatus.CONFLICT),
    CERTIFICATE_EXPIRED(4254, "Certificate has expired", HttpStatus.CONFLICT),
    CERTIFICATE_NOT_FOUND(4250, "Certificate does not exist", HttpStatus.NOT_FOUND),
    CERTIFICATE_VERSION_CONFLICT(4251, "The certificate was updated, please reload", HttpStatus.CONFLICT),
    CERTIFICATE_DATE_INVALID(4252, "Expiry date must be on or after the issue date", HttpStatus.BAD_REQUEST),

    WORK_SHIFT_NOT_FOUND(4300, "Work shift does not exist", HttpStatus.NOT_FOUND),
    WORK_SHIFT_VERSION_CONFLICT(4301, "The work shift was updated, please reload", HttpStatus.CONFLICT),
    WORK_SHIFT_TIME_INVALID(4302, "End time must be after start time", HttpStatus.BAD_REQUEST),
    WORK_SHIFT_DATE_INVALID(4303, "Invalid work date or registration deadline", HttpStatus.BAD_REQUEST),
    WORK_SHIFT_NOT_OPEN(4304, "The work shift is not open for registration", HttpStatus.CONFLICT),
    WORK_SHIFT_FULL(4305, "The work shift is full", HttpStatus.CONFLICT),
    WORK_SHIFT_HAS_APPROVED_REGISTRATIONS(4306, "Cannot edit or cancel a shift that has approved registrations", HttpStatus.CONFLICT),
    WORK_SHIFT_STATUS_INVALID(4307, "Cannot move the work shift to the requested status", HttpStatus.CONFLICT),
    WORK_SHIFT_ALREADY_STARTED(4308, "The work shift has already started", HttpStatus.CONFLICT),

    REGISTRATION_NOT_FOUND(4350, "Shift registration does not exist", HttpStatus.NOT_FOUND),
    REGISTRATION_EXISTED(4351, "You have already registered for this work shift", HttpStatus.CONFLICT),
    REGISTRATION_NOT_PENDING(4352, "Only pending registrations can be processed", HttpStatus.CONFLICT),
    REGISTRATION_REJECTION_REASON_REQUIRED(4353, "A reason is required when rejecting a registration", HttpStatus.BAD_REQUEST),
    REGISTRATION_DEADLINE_PASSED(4354, "The shift registration deadline has passed", HttpStatus.CONFLICT),

    SCHEDULE_NOT_FOUND(4400, "Caregiver schedule does not exist", HttpStatus.NOT_FOUND),
    SCHEDULE_CONFLICT(4401, "The caregiver already has an overlapping schedule", HttpStatus.CONFLICT),
    SCHEDULE_STATUS_INVALID(4402, "Cannot move the schedule to the requested status", HttpStatus.CONFLICT),
    SCHEDULE_CANCELLATION_REASON_REQUIRED(4403, "A reason is required when cancelling a schedule", HttpStatus.BAD_REQUEST),
    SCHEDULE_VERSION_CONFLICT(4404, "The schedule was updated, please reload", HttpStatus.CONFLICT),
    TIME_OFF_NOT_FOUND(4405, "Time off does not exist", HttpStatus.NOT_FOUND),
    TIME_OFF_OVERLAP(4406, "Time off overlaps another time off of the caregiver", HttpStatus.CONFLICT),
    TIME_OFF_RANGE_INVALID(4407, "Invalid time off (end must be after start, at most 30 days)", HttpStatus.BAD_REQUEST),
    TIME_OFF_HAS_BOOKINGS(4408, "Time off overlaps assigned care occurrences; ask the manager to reassign them first", HttpStatus.CONFLICT),
    AVAILABILITY_RANGE_TOO_LONG(4409, "Lookup range is invalid or exceeds 31 days", HttpStatus.BAD_REQUEST),
    TIME_OFF_TIME_REQUIRED(4410, "Time off start and end are required", HttpStatus.BAD_REQUEST),
    TIME_OFF_REASON_TOO_LONG(4411, "Time off reason must not exceed 500 characters", HttpStatus.BAD_REQUEST),
    SLOTS_INVALID(4412, "Slot list must have between 1 and 60 slots", HttpStatus.BAD_REQUEST),
    SLOT_RANGE_INVALID(4413, "Invalid slot (missing time or end is not after start)", HttpStatus.BAD_REQUEST),
    CAREGIVER_ID_REQUIRED(4414, "Caregiver ID is required", HttpStatus.BAD_REQUEST),
    SCHEDULE_HAS_BOOKINGS(4415, "The schedule has assigned care occurrences and cannot be cancelled", HttpStatus.CONFLICT),

    IDENTITY_UNAVAILABLE(4500, "Account service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    IDENTITY_USER_NOT_FOUND(4501, "Account does not exist", HttpStatus.NOT_FOUND),
    IDENTITY_USER_INVALID(4502, "Account is inactive or does not have the required role", HttpStatus.CONFLICT),
    IDENTITY_CENTER_MISMATCH(4503, "The account belongs to another center. Create an account for this center first", HttpStatus.CONFLICT),

    MEDIA_REQUIRED(4600, "At least one file must be selected", HttpStatus.BAD_REQUEST),
    MEDIA_LIMIT_EXCEEDED(4601, "At most 10 files can be uploaded at a time", HttpStatus.BAD_REQUEST),
    MEDIA_NOT_FOUND(4602, "File does not exist or does not belong to this record", HttpStatus.NOT_FOUND),
    MEDIA_SERVICE_UNAVAILABLE(4603, "File service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),

    REMOTE_COMMAND_REJECTED(4703, "A dependent service rejected the command; check configuration or data", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(4700, "Could not authenticate with a dependent service, please try again later", HttpStatus.SERVICE_UNAVAILABLE),
    CATALOG_UNAVAILABLE(4701, "Catalog service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    // Spec ghi 4700 nhưng 4700 đã là SERVICE_TOKEN_UNAVAILABLE (PRV-01) → dùng 4702.
    BOOKING_SERVICE_UNAVAILABLE(4702, "Booking service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE);
    private int code;
    private String message;
    private HttpStatusCode statusCode;
}
