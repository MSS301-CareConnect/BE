package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.client.ProviderServiceClient;
import mss301.bookingservice.client.dto.ProviderCenterResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.BookingAction;
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
    ConcurrentHashMap<UUID, UUID> managerIds = new ConcurrentHashMap<>();
    ConcurrentHashMap<UUID, Instant> expiresAt = new ConcurrentHashMap<>();

    @Override
    public void notifyLifecycle(Booking booking, List<BookingOccurrence> occurrences, BookingAction action) {
        NotificationType type = switch (action) {
            case ACCEPT -> NotificationType.BOOKING_ACCEPTED;
            case REJECT, EXPIRE -> NotificationType.BOOKING_REJECTED;
            case PAY_DEPOSIT -> NotificationType.BOOKING_CONFIRMED;
            case CANCEL_BY_CUSTOMER, CANCEL_BY_CENTER, CANCEL_BY_SYSTEM, FINALIZE_CANCELLED -> NotificationType.BOOKING_CANCELLED;
            case FINALIZE -> NotificationType.BOOKING_COMPLETED;
            case START -> null;
        };
        if (type == null) {
            return;
        }
        List<UUID> recipients = new ArrayList<>();
        recipients.add(booking.getCustomerId());
        if (action != BookingAction.FINALIZE) {
            occurrences.stream().map(BookingOccurrence::getCaregiverUserId).filter(Objects::nonNull)
                    .distinct().forEach(recipients::add);
        }
        String body = "Lịch " + booking.getBookingCode();
        if (action == BookingAction.ACCEPT && booking.getDepositDueAt() != null) {
            body += ": vui lòng thanh toán cọc trước " + booking.getDepositDueAt();
        }
        notificationService.notify(recipients, type, Map.of("body", body), "BOOKING", booking.getId());
        if (action != BookingAction.ACCEPT && action != BookingAction.REJECT) {
            notifyCenter(booking.getCenterId(), type, "BOOKING", booking.getId());
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
    public void notifyCenter(UUID centerId, NotificationType type, String refType, UUID refId) {
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
                    notificationService.notify(List.of(managerId), type, Map.of(), refType, refId);
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
