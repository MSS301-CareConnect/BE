package mss301.paymentservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Dải mã PaymentService: 6000–6999. Không đổi số / key đã có (6000–6016); mã mới đánh tiếp.
@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(6000, "System error", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(6001, "Invalid request data", HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(6002, "You are not logged in", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(6003, "You do not have permission to perform this action", HttpStatus.FORBIDDEN),
    PAYMENT_NOT_FOUND(6004, "Payment not found", HttpStatus.NOT_FOUND),
    BOOKING_NOT_PAYABLE(6005, "Booking is not awaiting payment", HttpStatus.CONFLICT),
    PAYMENT_ALREADY_SUCCESS(6006, "Booking has already been paid", HttpStatus.CONFLICT),
    PAYMENT_PROVIDER_DISABLED(6007, "Payment gateway is not configured", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_SIGNATURE(6008, "Invalid callback signature", HttpStatus.BAD_REQUEST),
    AMOUNT_MISMATCH(6009, "Callback amount does not match", HttpStatus.CONFLICT),
    BOOKING_SERVICE_UNAVAILABLE(6010, "BookingService is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    SERVICE_TOKEN_UNAVAILABLE(6011, "Could not obtain a service token", HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_GATEWAY_ERROR(6012, "Could not create a payment session", HttpStatus.BAD_GATEWAY),
    PATH_NOT_FOUND(6013, "Path does not exist", HttpStatus.NOT_FOUND),
    CONCURRENT_UPDATE(6014, "The payment was just updated by another operation, please try again", HttpStatus.CONFLICT),
    SERVICE_UNAVAILABLE(6015, "Payment service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_KEY(6016, "Invalid validation message key", HttpStatus.BAD_REQUEST),
    BOOKING_NOT_FOUND(6017, "Booking not found", HttpStatus.NOT_FOUND),
    BOOKING_PRICE_MISSING(6018, "Booking has occurrences without a price and cannot be paid yet", HttpStatus.CONFLICT),
    NO_EFFECTIVE_POLICY(6019, "No payment policy is currently in effect", HttpStatus.SERVICE_UNAVAILABLE),
    REFUND_EXCEEDS_PAID(6020, "Refund amount exceeds the amount the customer has paid", HttpStatus.CONFLICT),
    IDEMPOTENCY_KEY_REQUIRED(6021, "Idempotency-Key is required", HttpStatus.BAD_REQUEST),
    REFUND_NOT_FOUND(6022, "Refund request not found", HttpStatus.NOT_FOUND),
    REFUND_NOT_RETRYABLE(6023, "Only failed refund requests can be retried", HttpStatus.CONFLICT),
    INVOICE_NOT_FOUND(6024, "Invoice not found", HttpStatus.NOT_FOUND),
    INVOICE_NOT_PAYABLE(6025, "Invoice is not awaiting payment", HttpStatus.CONFLICT),
    WAIVE_REASON_REQUIRED(6026, "Please enter a reason for waiving the debt", HttpStatus.BAD_REQUEST),
    CENTER_SCOPE_REQUIRED(6027, "Manager account is not assigned to a center", HttpStatus.FORBIDDEN),
    BOOKING_ID_REQUIRED(6028, "Booking ID is required", HttpStatus.BAD_REQUEST),
    REFUND_REASON_REQUIRED(6029, "Refund reason is required", HttpStatus.BAD_REQUEST),
    CANCELLED_AT_REQUIRED(6030, "Cancellation time is required", HttpStatus.BAD_REQUEST),
    OCCURRENCES_REQUIRED(6031, "Occurrence list is required", HttpStatus.BAD_REQUEST),
    OCCURRENCE_ID_REQUIRED(6032, "Occurrence ID is required", HttpStatus.BAD_REQUEST),
    REFUND_AMOUNT_INVALID(6033, "Refund amount must be greater than 0", HttpStatus.BAD_REQUEST),
    COMPLAINT_ID_REQUIRED(6034, "Complaint ID is required", HttpStatus.BAD_REQUEST),
    PROVIDER_REQUIRED(6035, "Please select a payment gateway", HttpStatus.BAD_REQUEST),
    TEXT_TOO_LONG(6036, "Content is too long", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_WAIVABLE(6037, "Only pending or overdue invoices can be waived", HttpStatus.CONFLICT),
    BOOKING_COMMAND_REJECTED(6038, "BookingService rejected the update", HttpStatus.CONFLICT),
    OCCURRENCE_STATUS_REQUIRED(6039, "Final occurrence status is required", HttpStatus.BAD_REQUEST),
    // Ví + rút tiền (2026-11-09).
    WALLET_NOT_FOUND(6040, "Wallet not found", HttpStatus.NOT_FOUND),
    INSUFFICIENT_WALLET_BALANCE(6041, "The amount exceeds your available balance", HttpStatus.CONFLICT),
    WITHDRAWAL_NOT_FOUND(6042, "Withdrawal request not found", HttpStatus.NOT_FOUND),
    WITHDRAWAL_STATUS_INVALID(6043, "The withdrawal request is no longer in a state that allows this action",
            HttpStatus.CONFLICT),
    WITHDRAWAL_AMOUNT_REQUIRED(6044, "Please enter the amount to withdraw", HttpStatus.BAD_REQUEST),
    WITHDRAWAL_AMOUNT_INVALID(6045, "The amount must be a positive whole number of dong", HttpStatus.BAD_REQUEST),
    BANK_NAME_REQUIRED(6046, "Please enter the bank name", HttpStatus.BAD_REQUEST),
    BANK_ACCOUNT_NUMBER_REQUIRED(6047, "Please enter the account number", HttpStatus.BAD_REQUEST),
    BANK_ACCOUNT_NUMBER_INVALID(6048, "The account number must be 6 to 20 digits", HttpStatus.BAD_REQUEST),
    ACCOUNT_HOLDER_NAME_REQUIRED(6049, "Please enter the account holder name", HttpStatus.BAD_REQUEST),
    REJECT_REASON_REQUIRED(6050, "Please enter a reason for rejecting", HttpStatus.BAD_REQUEST),
    TRANSFER_REFERENCE_REQUIRED(6051, "Please enter the bank transfer reference", HttpStatus.BAD_REQUEST),
    WALLET_NOT_WITHDRAWABLE(6052, "This wallet cannot be withdrawn from", HttpStatus.FORBIDDEN);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
