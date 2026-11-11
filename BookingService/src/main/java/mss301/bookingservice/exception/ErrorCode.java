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
    NOTIFICATION_NOT_FOUND(5940, "Notification does not exist", HttpStatus.NOT_FOUND),
    TOO_MANY_RECIPIENTS(5941, "At most 500 recipients are allowed", HttpStatus.BAD_REQUEST),
    OUTBOX_NOT_FOUND(5960, "Outbox job does not exist", HttpStatus.NOT_FOUND),
    OUTBOX_NOT_FAILED(5961, "Only FAILED outbox jobs can be requeued / skipped", HttpStatus.CONFLICT),
    TASK_NOT_FOUND(5962, "Task does not exist", HttpStatus.NOT_FOUND),
    TASK_SKIP_REASON_REQUIRED(5804, "A reason is required to skip the task", HttpStatus.BAD_REQUEST),
    INVALID_VITALS(5821, "Invalid vital signs", HttpStatus.BAD_REQUEST),
    TOO_MANY_CUSTOM_TASKS(5822, "At most 20 tasks can be added", HttpStatus.BAD_REQUEST),
    REPORT_NOT_FOUND(5963, "Report does not exist", HttpStatus.NOT_FOUND),
    REPORT_ALREADY_SUBMITTED(5807, "A submitted report cannot be edited", HttpStatus.CONFLICT),
    REPORT_SESSION_NOT_ENDED(5809, "The care session has not ended", HttpStatus.CONFLICT),
    TOO_MANY_REPORT_PHOTOS(5810, "A report can have at most 5 photos", HttpStatus.BAD_REQUEST),
    MEDIA_SERVICE_UNAVAILABLE(5811, "File service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    REPORT_EXISTS(5816, "The care session already has a report", HttpStatus.CONFLICT),
    REPORT_PHOTO_NOT_FOUND(5964, "The photo does not belong to the report", HttpStatus.NOT_FOUND),
    REVIEW_NOT_ALLOWED(5900, "Not eligible to review yet", HttpStatus.CONFLICT),
    REVIEW_ALREADY_EXISTS(5901, "You have already reviewed the caregiver for this booking", HttpStatus.CONFLICT),
    RATING_OUT_OF_RANGE(5902, "Rating must be between 1 and 5", HttpStatus.BAD_REQUEST),
    REVIEW_NOT_FOUND(5903, "Review does not exist", HttpStatus.NOT_FOUND),
    REPLY_ALREADY_EXISTS(5904, "The review has already been replied to", HttpStatus.CONFLICT),
    COMPLAINT_NOT_FOUND(5920, "Complaint does not exist", HttpStatus.NOT_FOUND),
    INVALID_COMPLAINT_TRANSITION(5921, "Cannot change the complaint status", HttpStatus.CONFLICT),
    COMPLAINT_CONTENT_REQUIRED(5922, "Complaint content is required", HttpStatus.BAD_REQUEST),
    REFUND_AMOUNT_EXCEEDS_PAID(5923, "Refund amount exceeds the remaining refundable amount", HttpStatus.CONFLICT),
    RESOLUTION_REQUIRED(5924, "A reason or resolution is required", HttpStatus.BAD_REQUEST),
    COMPLAINT_BOOKING_INVALID(5925, "The booking is not eligible for a complaint", HttpStatus.CONFLICT),
    COMPLAINT_WINDOW_CLOSED(5926, "Complaints can only be filed for occurrences that took place, within 7 days after they end", HttpStatus.CONFLICT),
    COMPLAINT_ALREADY_OPEN(5927, "This care occurrence already has a complaint being processed", HttpStatus.CONFLICT),
    COMPLAINT_ESCALATE_TOO_EARLY(5928, "Can only escalate to admin when the center has not handled it after 48 hours", HttpStatus.CONFLICT),
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(5000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_KEY(5001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(5002, "CSRF token is missing or invalid; call GET /auth/csrf and send the X-XSRF-TOKEN header", HttpStatus.FORBIDDEN),
    INVALID_PAGE(5003, "Invalid pagination parameters", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(5004, "Booking service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(5005, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(5006, "The data was just changed by someone else, please reload and try again", HttpStatus.CONFLICT),
    CENTER_SCOPE_REQUIRED(5007, "The account is not assigned to a care center", HttpStatus.FORBIDDEN),
    TEXT_TOO_LONG(5008, "Content exceeds the allowed length", HttpStatus.BAD_REQUEST),

    // BOOKING
    BOOKING_NOT_FOUND(5100, "Booking does not exist", HttpStatus.NOT_FOUND),
    INVALID_BOOKING_TRANSITION(5101, "This action is not allowed in the booking's current status", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REUSED(5102, "Idempotency-Key has already been used for a different booking request", HttpStatus.CONFLICT),
    INVALID_IDEMPOTENCY_KEY(5103, "Idempotency-Key must be at most 100 characters of letters, digits, '-', '_' or '.'", HttpStatus.BAD_REQUEST),
    RECIPIENT_ID_REQUIRED(5104, "Care recipient is required", HttpStatus.BAD_REQUEST),
    CENTER_ID_REQUIRED(5105, "Care center is required", HttpStatus.BAD_REQUEST),
    REASON_REQUIRED(5106, "Reason is required", HttpStatus.BAD_REQUEST),
    BOOKING_ALREADY_STARTED(5107, "The first care occurrence has already started; the booking cannot be cancelled", HttpStatus.CONFLICT),
    SERVICES_REQUIRED(5108, "At least one service must be selected", HttpStatus.BAD_REQUEST),
    TOO_MANY_SERVICES(5109, "A booking can have at most 10 services", HttpStatus.BAD_REQUEST),
    DUPLICATE_SERVICE(5110, "Each service can only be selected once", HttpStatus.BAD_REQUEST),
    INVALID_SORT(5112, "Sorting is only allowed by createdAt, startDate or deadlineAt, direction asc / desc", HttpStatus.BAD_REQUEST),
    ADDRESS_LINE_REQUIRED(5113, "Street address is required", HttpStatus.BAD_REQUEST),
    PROVINCE_REQUIRED(5114, "Province / city is required", HttpStatus.BAD_REQUEST),
    SERVICE_ADDRESS_NOT_EDITABLE(5115, "The care address can only be changed while the booking still has upcoming occurrences and no visit is in progress", HttpStatus.CONFLICT),
    CARE_SERVICE_ID_REQUIRED(5111, "Care service is required", HttpStatus.BAD_REQUEST),
    ADDRESS_CHANGE_TOO_LATE(5117, "Only visits starting at least 24 hours from now can get a new care address", HttpStatus.CONFLICT),
    CENTER_OUT_OF_SERVICE_AREA(5118, "Care address is outside the center's operating area", HttpStatus.BAD_REQUEST),
    BOOKING_EXPIRED(5116, "The booking passed its deadline and has expired", HttpStatus.CONFLICT),

    // Spec v3: Đổi caregiver theo yêu cầu khách
    CAREGIVER_CHANGE_REQUEST_NOT_FOUND(5170, "Caregiver change request does not exist", HttpStatus.NOT_FOUND),
    CAREGIVER_CHANGE_REQUEST_NOT_PENDING(5171, "The caregiver change request is no longer pending", HttpStatus.CONFLICT),
    TOO_MANY_CHANGE_REQUESTS(5172, "A booking can have at most 3 caregiver change requests", HttpStatus.CONFLICT),
    CHANGE_REQUEST_TOO_LATE(5173, "Only visits starting at least 12 hours from now can get a new caregiver", HttpStatus.CONFLICT),
    NO_REASSIGNABLE_OCCURRENCES(5174, "There are no eligible upcoming care occurrences to reassign", HttpStatus.CONFLICT),
    // 2026-11-09: khách chỉ đổi caregiver cho cả booking.
    CAREGIVER_CHANGE_SCOPE_INVALID(5175, "A caregiver change applies to every remaining visit of the booking", HttpStatus.BAD_REQUEST),

    // LỊCH / BUỔI CHĂM SÓC
    BOOKING_TYPE_REQUIRED(5200, "Booking type is required (SHIFT, RECURRING, CUSTOM)", HttpStatus.BAD_REQUEST),
    START_DATE_REQUIRED(5201, "Start date is required", HttpStatus.BAD_REQUEST),
    START_TIME_REQUIRED(5202, "Start time is required", HttpStatus.BAD_REQUEST),
    END_TIME_REQUIRED(5203, "End time is required", HttpStatus.BAD_REQUEST),
    END_DATE_REQUIRED(5204, "Recurring bookings require an end date", HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(5205, "End date must be on or after the start date", HttpStatus.BAD_REQUEST),
    START_DATE_IN_PAST(5206, "Start date cannot be in the past", HttpStatus.BAD_REQUEST),
    OVERNIGHT_NOT_SUPPORTED(5207, "End time must be after start time (overnight shifts are not supported yet)", HttpStatus.BAD_REQUEST),
    DAYS_OF_WEEK_REQUIRED(5208, "Weekly schedules require at least one day of the week", HttpStatus.BAD_REQUEST),
    INVALID_DAYS_OF_WEEK(5209, "Days of the week must be from 1 (Monday) to 7 (Sunday), without duplicates", HttpStatus.BAD_REQUEST),
    DAYS_OF_WEEK_NOT_ALLOWED(5210, "Days of the week can only be selected for weekly schedules", HttpStatus.BAD_REQUEST),
    NO_OCCURRENCE_IN_RANGE(5211, "The selected date range contains no care occurrences", HttpStatus.BAD_REQUEST),
    TOO_MANY_OCCURRENCES(5212, "Number of care occurrences exceeds the allowed limit", HttpStatus.BAD_REQUEST),
    SCHEDULE_SPAN_TOO_LONG(5213, "Booking period exceeds the allowed limit", HttpStatus.BAD_REQUEST),
    LEAD_TIME_TOO_SHORT(5214, "The first occurrence must start after the minimum notice period", HttpStatus.BAD_REQUEST),
    START_TOO_FAR(5235, "Bookings can only start within the next 90 days", HttpStatus.BAD_REQUEST),
    OCCURRENCE_TOO_SHORT(5236, "Each care occurrence must last at least 1 hour", HttpStatus.BAD_REQUEST),
    CAREGIVER_SCHEDULE_ID_REQUIRED(5237, "A SHIFT booking requires a caregiver schedule", HttpStatus.BAD_REQUEST),
    RECURRENCE_FREQUENCY_REQUIRED(5238, "Recurring bookings require a frequency (DAILY, WEEKLY)", HttpStatus.BAD_REQUEST),
    SCHEDULE_FIELD_NOT_ALLOWED(5239, "The schedule fields sent do not match the booking type", HttpStatus.BAD_REQUEST),
    OCCURRENCES_OVERLAP(5240, "Care occurrences in the same booking must not overlap", HttpStatus.BAD_REQUEST),
    SLOT_OUTSIDE_RECURRENCE_RANGE(5243, "Every edited occurrence of a recurring booking must fall between its start and end dates", HttpStatus.BAD_REQUEST),
    NO_VALID_PAYMENT_DEADLINE(5241, "Not enough time before the first occurrence for the center to confirm and for payment", HttpStatus.BAD_REQUEST),
    CAREGIVER_SCHEDULE_CONFLICT(5215, "The caregiver already has an overlapping schedule", HttpStatus.CONFLICT),
    RECIPIENT_SCHEDULE_CONFLICT(5216, "The care recipient already has an overlapping booking", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_FOUND(5217, "Care occurrence does not exist", HttpStatus.NOT_FOUND),
    OCCURRENCE_NOT_MODIFIABLE(5218, "Only care occurrences that have not started can be changed", HttpStatus.CONFLICT),
    INVALID_DATE_FILTER(5219, "Date filter range is invalid or exceeds the limit", HttpStatus.BAD_REQUEST),
    SESSION_WINDOW_NOT_OPEN(5222, "Check-in is only allowed within the center's check-in window around the occurrence start time", HttpStatus.CONFLICT),
    OCCURRENCE_ID_REQUIRED(5227, "Care occurrence ID is required", HttpStatus.BAD_REQUEST),
    INVALID_OCCURRENCE_TRANSITION(5221, "The care occurrence is not in a status that allows this action", HttpStatus.CONFLICT),
    BOOKING_NOT_CONFIRMED(5223, "Booking is not confirmed (no deposit yet) or has ended", HttpStatus.CONFLICT),
    BOOKING_NOT_FULLY_PAID(5242, "The customer has not paid the remaining amount of this booking yet", HttpStatus.CONFLICT),
    CAREGIVER_NOT_AVAILABLE(5226, "The caregiver has no working schedule (or is on time off) at the selected time", HttpStatus.CONFLICT),
    TOO_MANY_PENDING_BOOKINGS(5228, "You have too many pending bookings; please wait for the center to review them or cancel some", HttpStatus.CONFLICT),
    CAREGIVER_IDS_REQUIRED(5229, "Caregiver list is required", HttpStatus.BAD_REQUEST),
    SLOTS_REQUIRED(5230, "Time slot list is required", HttpStatus.BAD_REQUEST),
    INVALID_TIME_SLOT(5231, "Invalid time slot (end time must be after start time)", HttpStatus.BAD_REQUEST),
    TOO_MANY_CAREGIVER_IDS(5232, "At most 200 caregivers per check", HttpStatus.BAD_REQUEST),
    TOO_MANY_SLOTS(5233, "At most 60 time slots per check", HttpStatus.BAD_REQUEST),
    OCCURRENCE_NEEDS_REASSIGNMENT(5234, "The caregiver is no longer active; waiting for the center to reassign this occurrence", HttpStatus.CONFLICT),
    PRICE_PLAN_REQUIRED(5224, "A price plan must be selected for each service", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_UNIT_MISMATCH(5225, "Occurrence duration does not match the duration of the selected price plan", HttpStatus.BAD_REQUEST),
    SLOT_SERVICE_NOT_SELECTED(5244, "A time slot names a service that is not part of this booking", HttpStatus.BAD_REQUEST),
    SLOT_SERVICE_WITHOUT_SLOT(5245, "Every selected service needs at least one time slot", HttpStatus.BAD_REQUEST),
    SLOT_SERVICE_MIXED(5246, "Either every time slot names its service or none does", HttpStatus.BAD_REQUEST),
    SLOT_OUTSIDE_SHIFT(5247, "Every time slot must fall within the caregiver's chosen shift", HttpStatus.BAD_REQUEST),
    CAREGIVER_TRAVEL_BUFFER_CONFLICT(5248, "The caregiver needs at least 60 minutes between two different bookings", HttpStatus.CONFLICT),

    // Spec v3: Dời lịch & xác nhận thay thế khẩn cấp
    RESCHEDULE_LEAD_TIME_TOO_SHORT(5250, "Occurrences can only be rescheduled at least 24 hours in advance", HttpStatus.CONFLICT),
    RESCHEDULE_LIMIT_REACHED(5251, "Maximum number of reschedules reached for this care occurrence", HttpStatus.CONFLICT),
    REPLACEMENT_CONSENT_INVALID(5252, "No emergency replacement pending customer consent on this care occurrence", HttpStatus.CONFLICT),

    // 2026-11-09: caregiver xin hủy buổi được giao (trung tâm phân người khác).
    WITHDRAWAL_TOO_LATE(5260, "A visit can only be cancelled at least 24 hours before it starts", HttpStatus.CONFLICT),
    WITHDRAWAL_ALREADY_REQUESTED(5261, "You have already asked to cancel this visit", HttpStatus.CONFLICT),
    WITHDRAWAL_NOT_ALLOWED(5262, "This visit can no longer be cancelled", HttpStatus.CONFLICT),
    WITHDRAWAL_REASON_DETAIL_REQUIRED(5263, "Please describe the reason", HttpStatus.BAD_REQUEST),
    WITHDRAWAL_REASON_REQUIRED(5264, "Choose a reason for cancelling", HttpStatus.BAD_REQUEST),
    // 2026-11-10: ngày có nhiều buổi thì caregiver xin hủy cả ngày, không hủy lẻ một buổi.
    WITHDRAWAL_WHOLE_DAY_REQUIRED(5265, "This day has several visits, so cancel the whole day instead of one visit", HttpStatus.CONFLICT),

    // 2026-11-10: hồ sơ caregiver vắng ca (giải trình + kết luận của trung tâm).
    CAREGIVER_ABSENCE_NOT_FOUND(5270, "Absence record does not exist", HttpStatus.NOT_FOUND),
    CAREGIVER_ABSENCE_ALREADY_CONCLUDED(5271, "The center has already concluded this absence", HttpStatus.CONFLICT),
    ABSENCE_EXPLANATION_REQUIRED(5272, "Describe what happened (10–1000 characters)", HttpStatus.BAD_REQUEST),
    ABSENCE_DECISION_REQUIRED(5273, "Choose whether the absence is excused or confirmed", HttpStatus.BAD_REQUEST),
    ABSENCE_CONCLUSION_NOTE_REQUIRED(5274, "A note is required when excusing an absence", HttpStatus.BAD_REQUEST),

    // PHÂN CÔNG / TRUNG TÂM
    CAREGIVER_REQUIRED(5400, "A caregiver must be assigned before accepting the booking", HttpStatus.CONFLICT),
    CAREGIVER_ID_REQUIRED(5401, "Caregiver is required", HttpStatus.BAD_REQUEST),
    CAREGIVER_NOT_FOUND(5402, "Caregiver does not exist", HttpStatus.NOT_FOUND),
    CAREGIVER_NOT_IN_CENTER(5403, "The caregiver does not belong to the booking's center", HttpStatus.BAD_REQUEST),
    CAREGIVER_NOT_VERIFIED(5404, "The caregiver is not verified", HttpStatus.CONFLICT),
    CAREGIVER_NOT_ACTIVE(5405, "The caregiver is currently inactive", HttpStatus.CONFLICT),
    CAREGIVER_SERVICE_NOT_SUPPORTED(5406, "The caregiver cannot perform all selected services", HttpStatus.BAD_REQUEST),
    CENTER_NOT_FOUND(5407, "Care center does not exist", HttpStatus.NOT_FOUND),
    CENTER_NOT_ACTIVE(5408, "The care center is not currently accepting bookings", HttpStatus.CONFLICT),
    CAREGIVER_SCHEDULE_NOT_FOUND(5409, "Caregiver schedule does not exist", HttpStatus.NOT_FOUND),
    CAREGIVER_SCHEDULE_MISMATCH(5410, "The schedule does not belong to the selected center and caregiver", HttpStatus.BAD_REQUEST),
    CAREGIVER_SCHEDULE_NOT_AVAILABLE(5411, "This schedule is no longer available", HttpStatus.CONFLICT),
    INVALID_BOOKING_POLICY(5413, "Booking policy values are invalid or inconsistent", HttpStatus.BAD_REQUEST),
    BOOKING_POLICY_VALUE_REQUIRED(5414, "All booking policy values are required", HttpStatus.BAD_REQUEST),

    // TÍCH HỢP CARE CATALOG
    CUSTOMER_HAS_OUTSTANDING_DEBT(5157, "You have overdue unpaid invoices; please pay them before making a new booking", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_CANCELLABLE(5150, "Some care occurrences have started, ended or been cancelled — cannot cancel", HttpStatus.CONFLICT),
    CANCEL_SCOPE_NOT_ALLOWED(5151, "A booking without a deposit can only be cancelled entirely, not per occurrence", HttpStatus.CONFLICT),
    OCCURRENCE_NOT_IN_BOOKING(5153, "The care occurrence does not belong to this booking", HttpStatus.BAD_REQUEST),
    CANCELLATION_PREVIEW_EXPIRED(5159, "The refund amount has changed or the preview has expired, please review again", HttpStatus.CONFLICT),
    SYSTEM_CANCELLATION_REASON_REQUIRED(5160, "System cancellation reason is required (CENTER_LOCKED, CAREGIVER_DEACTIVATED)", HttpStatus.BAD_REQUEST),
    PAYMENT_SERVICE_UNAVAILABLE(5154, "Payment service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    CARE_CATALOG_UNAVAILABLE(5300, "Catalog / pricing service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    PRICE_PLAN_NOT_FOUND(5301, "Service price plan does not exist or is invalid", HttpStatus.NOT_FOUND),
    CENTER_SERVICE_NOT_SUPPORTED(5302, "The center does not offer the selected care service", HttpStatus.BAD_REQUEST),

    // TÍCH HỢP IDENTITY
    SESSION_NOT_FOUND(5800, "Care session does not exist", HttpStatus.NOT_FOUND),
    SESSION_ALREADY_EXISTS(5801, "This care occurrence has already been checked in", HttpStatus.CONFLICT),
    SESSION_NOT_IN_PROGRESS(5802, "The care session has ended", HttpStatus.CONFLICT),
    INVALID_COORDINATES(5803, "Invalid coordinates", HttpStatus.BAD_REQUEST),
    SESSION_ANOTHER_IN_PROGRESS(5806, "Check out of your running care session before starting another one", HttpStatus.CONFLICT),
    SESSION_REVIEW_DECISION_REQUIRED(5818, "Choose whether the visit was delivered", HttpStatus.BAD_REQUEST),
    SESSION_REVIEW_NOTE_INVALID(5819, "Explain in 10–1000 characters why the visit was not delivered", HttpStatus.BAD_REQUEST),
    SESSION_REVIEW_NOT_PENDING(5820, "This care session is not waiting for the center's confirmation", HttpStatus.CONFLICT),
    CUSTOMER_REPORT_NOT_ALLOWED(5823, "This visit cannot be reported now", HttpStatus.CONFLICT),
    CUSTOMER_REPORT_ALREADY_SENT(5824, "You have already reported this visit", HttpStatus.CONFLICT),
    CUSTOMER_REPORT_NOTE_INVALID(5825, "Describe what happened in 10–1000 characters", HttpStatus.BAD_REQUEST),
    TASKS_PENDING(5805, "Some tasks are not completed or have no skip reason", HttpStatus.CONFLICT),
    NO_SHOW_NOTE_REQUIRED(5812, "Describe the customer no-show situation (10–1000 characters)", HttpStatus.BAD_REQUEST),
    NO_SHOW_NOT_REPORTED(5813, "The care occurrence has no customer no-show report", HttpStatus.CONFLICT),
    NO_SHOW_ALREADY_REPORTED(5814, "This care occurrence has already been reported as a customer no-show", HttpStatus.CONFLICT),
    NO_SHOW_DECISION_REQUIRED(5815, "Choose to confirm or reject the customer no-show report", HttpStatus.BAD_REQUEST),
    NO_SHOW_WINDOW_INVALID(5817, "A no-show can only be reported from the occurrence start time until the check-in window closes", HttpStatus.CONFLICT),
    IDENTITY_UNAVAILABLE(5500, "Account service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    REMOTE_COMMAND_REJECTED(5155, "A dependent service rejected the command; check configuration or data", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(5501, "Could not authenticate with a dependent service, please try again later", HttpStatus.SERVICE_UNAVAILABLE),

    // TÍCH HỢP CARE RECIPIENT
    CARE_RECIPIENT_UNAVAILABLE(5600, "Care recipient service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE),
    RECIPIENT_NOT_FOUND(5601, "Care recipient profile does not exist", HttpStatus.NOT_FOUND),
    RECIPIENT_NOT_ACTIVE(5602, "Care recipient profile is suspended; cannot book", HttpStatus.CONFLICT),

    // TÍCH HỢP PROVIDER
    PROVIDER_UNAVAILABLE(5700, "Center / caregiver service is temporarily unavailable, please try again", HttpStatus.SERVICE_UNAVAILABLE);

    private int code;
    private String message;
    private HttpStatusCode statusCode;

}
