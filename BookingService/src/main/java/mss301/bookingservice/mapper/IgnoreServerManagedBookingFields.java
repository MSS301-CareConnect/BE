package mss301.bookingservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field riêng của Booking do server quản lý (mã, trạng thái + mốc thời gian của state machine, múi giờ, idempotency,
 * version) — không bao giờ lấy từ request. Gắn CÙNG {@link IgnoreServerManagedFields}.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "bookingCode", ignore = true)
@Mapping(target = "status", ignore = true)
@Mapping(target = "timezone", ignore = true)
@Mapping(target = "acceptanceDueAt", ignore = true)
@Mapping(target = "acceptedBy", ignore = true)
@Mapping(target = "acceptedAt", ignore = true)
@Mapping(target = "assignmentDueAt", ignore = true)
@Mapping(target = "depositDueAt", ignore = true)
@Mapping(target = "remainingPaymentDueAt", ignore = true)
@Mapping(target = "fullyPaidAt", ignore = true)
@Mapping(target = "remainingReminderMinutes", ignore = true)
@Mapping(target = "confirmedAt", ignore = true)
@Mapping(target = "startedAt", ignore = true)
@Mapping(target = "completedAt", ignore = true)
@Mapping(target = "cancelledAt", ignore = true)
@Mapping(target = "cancelledBy", ignore = true)
@Mapping(target = "cancelReason", ignore = true)
@Mapping(target = "rejectedBy", ignore = true)
@Mapping(target = "rejectedAt", ignore = true)
@Mapping(target = "rejectedReason", ignore = true)
@Mapping(target = "idempotencyKey", ignore = true)
@Mapping(target = "idempotencyHash", ignore = true)
@Mapping(target = "version", ignore = true)
// Số tiền thanh toán đầu do PaymentService ghi khi xác nhận (PAY-08); tỷ lệ chụp từ center policy truyền qua tham số.
@Mapping(target = "depositAmount", ignore = true)
public @interface IgnoreServerManagedBookingFields {
}
