package mss301.bookingservice.service.Impl;

import mss301.bookingservice.config.BookingProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingExpiredReason;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import java.util.ArrayList;
import java.util.Objects;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingNotificationServiceImpl implements BookingNotificationService {
    ProviderServiceClient providerServiceClient;
    NotificationService notificationService;
    Clock clock;
    TransactionTemplate transactionTemplate;
    BookingProperties bookingProperties;
    static DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    static Map<BookingExpiredReason, String> EXPIRED_BODIES = Map.of(
            BookingExpiredReason.CENTER_CONFIRMATION_TIMEOUT, "trung tâm chưa xác nhận kịp hạn, chưa phát sinh thanh toán",
            BookingExpiredReason.CAREGIVER_CONFIRMATION_TIMEOUT, "người chăm sóc chưa trả lời kịp hạn, chưa phát sinh thanh toán",
            BookingExpiredReason.ASSIGNMENT_TIMEOUT, "trung tâm chưa phân người chăm sóc kịp hạn, chưa phát sinh thanh toán",
            BookingExpiredReason.INITIAL_PAYMENT_TIMEOUT, "chưa thanh toán khoản đầu đúng hạn",
            BookingExpiredReason.PAYMENT_OVERDUE, "chưa thanh toán phần còn lại đúng hạn");
    ConcurrentHashMap<UUID, UUID> managerIds = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, Instant> expiresAt = new ConcurrentHashMap<>();

    @Override
    public void notifyLifecycle(Booking booking, List<BookingOccurrence> occurrences, BookingAction action) {
        NotificationType type = switch (action) {
            case ACCEPT, ACCEPT_WITHOUT_CAREGIVER, ASSIGN_CAREGIVER, CAREGIVER_ACCEPT -> NotificationType.BOOKING_ACCEPTED;
            case REJECT, CAREGIVER_DECLINE -> NotificationType.BOOKING_REJECTED;
            case HAND_OVER_TO_CENTER -> NotificationType.BOOKING_HANDED_OVER;
            case EXPIRE -> NotificationType.BOOKING_EXPIRED;
            case PAY_INITIAL_PAYMENT -> NotificationType.BOOKING_CONFIRMED;
            case CANCEL_BY_CUSTOMER, CANCEL_BY_CENTER, CANCEL_BY_SYSTEM, FINALIZE_CANCELLED -> NotificationType.BOOKING_CANCELLED;
            case FINALIZE -> NotificationType.BOOKING_COMPLETED;
            case START -> null;
        };
        if (type == null) {
            return;
        }
        List<UUID> recipients = new ArrayList<>();
        recipients.add(booking.getCustomerId());
        // Caregiver vừa tự nhận / từ chối đơn thì không báo lại chính họ (CR-3). SHIFT tự nhận (acceptedBy NULL = hệ thống)
        // thì caregiver chưa biết gì → báo riêng bên dưới.
        boolean autoAccepted = action == BookingAction.CAREGIVER_ACCEPT && booking.getAcceptedBy() == null;
        boolean caregiverAction = action == BookingAction.CAREGIVER_ACCEPT || action == BookingAction.CAREGIVER_DECLINE;
        if (action != BookingAction.FINALIZE && !caregiverAction) {
            occurrences.stream().map(BookingOccurrence::getCaregiverUserId).filter(Objects::nonNull)
                    .distinct().forEach(recipients::add);
        }
        String body = "Lịch " + booking.getBookingCode();
        boolean awaitingPayment = action == BookingAction.ACCEPT || action == BookingAction.ASSIGN_CAREGIVER
                || action == BookingAction.CAREGIVER_ACCEPT;
        if (awaitingPayment && booking.getDepositDueAt() != null) {
            body += ": vui lòng thanh toán trước " + formatTime(booking.getDepositDueAt());
        }
        if (action == BookingAction.EXPIRE && booking.getExpiredReason() != null) {
            body += ": " + EXPIRED_BODIES.get(booking.getExpiredReason());
        }
        if (action == BookingAction.ACCEPT_WITHOUT_CAREGIVER) {
            body += ": trung tâm đã xác nhận, đang phân người chăm sóc";
        }
        if (action == BookingAction.HAND_OVER_TO_CENTER) {
            body += ": người chăm sóc đã chọn không nhận, trung tâm sẽ phân người khác";
        }
        notificationService.notify(recipients, type, Map.of("body", body), "BOOKING", booking.getId());
        if (autoAccepted) {
            List<UUID> caregiverUserIds = occurrences.stream().map(BookingOccurrence::getCaregiverUserId)
                    .filter(Objects::nonNull).distinct().toList();
            String caregiverBody = "Lịch " + booking.getBookingCode() + " đã được đặt vào ca của bạn, chờ khách thanh toán";
            notificationService.notify(caregiverUserIds, type, Map.of("body", caregiverBody), "BOOKING", booking.getId());
        }
        // Thao tác do chính trung tâm làm (xác nhận / phân công / từ chối) thì không báo lại trung tâm.
        boolean centerAction = action == BookingAction.ACCEPT || action == BookingAction.ACCEPT_WITHOUT_CAREGIVER
                || action == BookingAction.ASSIGN_CAREGIVER || action == BookingAction.REJECT;
        if (!centerAction) {
            notifyCenter(booking.getCenterId(), type, Map.of("body", body), "BOOKING", booking.getId());
        }
    }

    @Override
    public void notifyOccurrence(BookingOccurrence occurrence, NotificationType type) {
        List<UUID> recipients = new ArrayList<>();
        recipients.add(occurrence.getBooking().getCustomerId());
        if (occurrence.getCaregiverUserId() != null) {
            recipients.add(occurrence.getCaregiverUserId());
        }
        notificationService.notify(recipients, type, Map.of(), "BOOKING_OCCURRENCE", occurrence.getId());
        notifyCenter(occurrence.getBooking().getCenterId(), type, "BOOKING_OCCURRENCE", occurrence.getId());
    }

    @Override
    public void notifyOccurrencesCancelled(Booking booking, List<BookingOccurrence> occurrences) {
        List<UUID> recipients = new ArrayList<>();
        recipients.add(booking.getCustomerId());
        occurrences.stream().map(BookingOccurrence::getCaregiverUserId).filter(Objects::nonNull)
                .distinct().forEach(recipients::add);
        String body = occurrences.size() + " buổi của lịch " + booking.getBookingCode() + " đã bị hủy";
        notificationService.notify(recipients, NotificationType.OCCURRENCE_CANCELLED, Map.of("body", body),
                "BOOKING", booking.getId());
        notifyCenter(booking.getCenterId(), NotificationType.OCCURRENCE_CANCELLED, "BOOKING", booking.getId());
    }

    @Override
    public void notifyRemainingDue(Booking booking) {
        String body = "Lịch " + booking.getBookingCode() + ": vui lòng thanh toán phần còn lại trước "
                + formatTime(booking.getRemainingPaymentDueAt());
        notificationService.notify(List.of(booking.getCustomerId()), NotificationType.REMAINING_DUE, Map.of("body", body),
                "BOOKING", booking.getId());
    }

    @Override
    public void notifyCaregiverRequest(Booking booking) {
        String body = "Lịch " + booking.getBookingCode() + ": vui lòng trả lời trước "
                + formatTime(booking.getAcceptanceDueAt());
        notificationService.notify(List.of(booking.getCaregiverUserId()), NotificationType.CAREGIVER_REQUEST,
                Map.of("body", body), "BOOKING", booking.getId());
    }

    @Override
    public void notifyAddressChanged(Booking booking, List<UUID> caregiverUserIds) {
        List<UUID> recipients = new ArrayList<>();
        recipients.add(booking.getCustomerId());
        recipients.addAll(caregiverUserIds);
        String body = "Lịch " + booking.getBookingCode() + ": địa chỉ mới " + booking.getServiceAddress();
        notificationService.notify(recipients, NotificationType.BOOKING_ADDRESS_CHANGED, Map.of("body", body),
                "BOOKING", booking.getId());
        notifyCenter(booking.getCenterId(), NotificationType.BOOKING_ADDRESS_CHANGED, "BOOKING", booking.getId());
    }

    // Giờ trong nội dung thông báo theo múi giờ booking (không in Instant UTC thô).
    private String formatTime(Instant instant) {
        return instant == null ? "" : TIME_FORMAT.withZone(bookingProperties.getSchedule().getTimezone()).format(instant);
    }

    @Override
    public void notifyAdmins(NotificationType type, String refType, UUID refId) {
        List<UUID> adminIds = bookingProperties.getNotification().getAdminUserIds();
        if (adminIds.isEmpty()) {
            log.warn("Chưa cấu hình BOOKING_ADMIN_USER_IDS — bỏ qua thông báo admin {} {}", type, refId);
            return;
        }
        notificationService.notify(adminIds, type, Map.of(), refType, refId);
    }

    @Override
    public void notifyCenterActionDue(Booking booking) {
        boolean confirming = booking.getStatus() == BookingStatus.PENDING_CENTER_CONFIRMATION;
        String body = "Lịch " + booking.getBookingCode() + (confirming
                ? ": cần xác nhận trước " + formatTime(booking.getAcceptanceDueAt())
                : ": cần phân người chăm sóc trước " + formatTime(booking.getAssignmentDueAt()));
        notifyCenter(booking.getCenterId(), NotificationType.CENTER_ACTION_DUE, Map.of("body", body), "BOOKING",
                booking.getId());
    }

    @Override
    public void notifyCenter(UUID centerId, NotificationType type, String refType, UUID refId) {
        notifyCenter(centerId, type, Map.of(), refType, refId);
    }

    @Override
    public void notifyCenter(UUID centerId, NotificationType type, Map<String, Object> params, String refType,
                             UUID refId) {
        Runnable action = () -> {
            try {
                UUID managerId = managerIds.get(centerId);
                if (managerId == null || !clock.instant().isBefore(expiresAt.getOrDefault(centerId, Instant.MIN))) {
                    RemoteApiResponse<ProviderCenterResponse> response = providerServiceClient.getCenter(centerId);
                    managerId = response == null || response.getResult() == null ? null : response.getResult().getManagerUserId();
                    if (managerId != null) {
                        managerIds.put(centerId, managerId);
                        expiresAt.put(centerId, clock.instant().plusSeconds(600));
                    }
                }
                if (managerId != null) {
                    notificationService.notify(List.of(managerId), type, params, refType, refId);
                }
            } catch (RuntimeException exception) {
                log.error("Không thông báo được manager trung tâm {}", centerId, exception);
            }
        };
        TransactionTemplate suspend = new TransactionTemplate(transactionTemplate.getTransactionManager());
        suspend.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        Runnable dispatch = () -> suspend.executeWithoutResult(status -> action.run());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            dispatch.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch.run();
            }
        });
    }
}
