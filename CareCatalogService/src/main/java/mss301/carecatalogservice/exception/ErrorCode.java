package mss301.carecatalogservice.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Dải mã của CareCatalogService: 3000–3999 (+ 401, 403, 404, 9999 dùng chung ý nghĩa giữa các service).
@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHENTICATED(401, "You are not authenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "You do not have permission to access this resource", HttpStatus.FORBIDDEN),
    PATH_NOT_FOUND(404, "Path not found", HttpStatus.NOT_FOUND),

    // CHUNG
    INVALID_REQUEST(3000, "Invalid request", HttpStatus.BAD_REQUEST),
    INVALID_KEY(3001, "Invalid message key", HttpStatus.BAD_REQUEST),
    CSRF_INVALID(3002, "CSRF token is missing or invalid; call GET /auth/csrf and send the X-XSRF-TOKEN header", HttpStatus.FORBIDDEN),
    INVALID_PAGE(3003, "Invalid pagination parameters", HttpStatus.BAD_REQUEST),
    SERVICE_UNAVAILABLE(3004, "Catalog service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    AUTH_DEPENDENCY_UNAVAILABLE(3005, "Authentication service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    CONCURRENT_UPDATE(3006, "The data was just changed by someone else, please reload and try again", HttpStatus.CONFLICT),

    // CARE SERVICES (Danh mục chuẩn)
    CARE_SERVICE_NOT_FOUND(3100, "Care service does not exist", HttpStatus.NOT_FOUND),
    CARE_SERVICE_NAME_REQUIRED(3101, "Service name is required", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_CATEGORY_REQUIRED(3102, "Category is required", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_STATUS_REQUIRED(3103, "Status is required", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_ID_REQUIRED(3104, "Care service ID is required", HttpStatus.BAD_REQUEST),
    SERVICE_NOT_ACTIVE(3105, "Care service is currently inactive", HttpStatus.CONFLICT),
    SERVICES_REQUIRED(3106, "At least one service is required", HttpStatus.BAD_REQUEST),
    CARE_SERVICE_IN_USE(3110, "Standard service is in use by centers — set it to INACTIVE instead of deleting", HttpStatus.CONFLICT),

    // CENTER SERVICES (Dịch vụ trung tâm)
    CENTER_SERVICE_NOT_FOUND(3200, "Center service does not exist", HttpStatus.NOT_FOUND),
    CENTER_ID_REQUIRED(3201, "Center ID is required", HttpStatus.BAD_REQUEST),
    CENTER_SERVICE_ID_REQUIRED(3202, "Center service ID is required", HttpStatus.BAD_REQUEST),
    CENTER_ACCESS_DENIED(3204, "You do not have permission to manage this center's services", HttpStatus.FORBIDDEN),
    CENTER_SERVICE_ALREADY_EXISTS(3205, "The center already offers this care service", HttpStatus.CONFLICT),
    CENTER_SERVICE_IN_USE(3206, "Center service still has active price plans — set it to INACTIVE instead of deleting", HttpStatus.CONFLICT),
    PRICE_RANGE_INVALID(3207, "Invalid price range (minimum price must be ≤ maximum price and non-negative)", HttpStatus.BAD_REQUEST),

    // PRICE PLANS
    PRICE_PLAN_NOT_FOUND(3300, "Price plan does not exist", HttpStatus.NOT_FOUND),
    PRICE_PLAN_NAME_REQUIRED(3301, "Price plan name is required", HttpStatus.BAD_REQUEST),
    PRICING_UNIT_REQUIRED(3302, "Pricing unit is required", HttpStatus.BAD_REQUEST),
    INVALID_DURATION(3303, "Duration (minutes) must be greater than 0; an hourly (HOUR) price plan must be 60 minutes", HttpStatus.BAD_REQUEST),
    INVALID_BASE_PRICE(3304, "Base price must be greater than or equal to 0", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_ID_REQUIRED(3305, "A price plan must be selected for each service", HttpStatus.BAD_REQUEST),
    PRICE_PLAN_NOT_ACTIVE(3306, "Price plan is not currently active", HttpStatus.CONFLICT);

    private int code;
    private String message;
    private HttpStatusCode statusCode;
}
