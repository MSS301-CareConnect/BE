package mss301.providerservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.service.ProviderOutboxService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ProviderOutboxScheduler {
    ProviderOutboxService providerOutboxService;

    @Scheduled(fixedDelayString = "${PROVIDER_OUTBOX_SCAN_INTERVAL:30s}")
    public void dispatch() {
        for (UUID id : providerOutboxService.findDueIds()) {
            try {
                providerOutboxService.dispatch(id);
            } catch (RuntimeException exception) {
                log.error("Không xử lý được provider outbox {}", id, exception);
            }
        }
    }
}
