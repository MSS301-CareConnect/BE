package mss301.bookingservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationHeartbeatScheduler {
    NotificationService notificationService;

    @Scheduled(fixedDelayString = "${BOOKING_NOTIFICATION_HEARTBEAT:25s}")
    public void heartbeat() {
        notificationService.heartbeat();
    }
}
