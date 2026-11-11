package mss301.providerservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.service.WorkShiftLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Mỗi phút (đầu phút). Đặt PROVIDER_SHIFT_LIFECYCLE_CRON=- để tắt (test tắt trong src/test/resources/application.yaml).
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftLifecycleScheduler {
    WorkShiftLifecycleService workShiftLifecycleService;

    // Định kỳ cập nhật trạng thái ca theo thời gian (mở, đóng, hoàn thành).
    @Scheduled(cron = "${provider.work-shift.lifecycle-cron:${PROVIDER_SHIFT_LIFECYCLE_CRON:0 * * * * *}}")
    public void advance() {
        try {
            workShiftLifecycleService.advance();
        } catch (RuntimeException exception) {
            log.error("Không cập nhật được vòng đời ca làm việc", exception);
        }
    }
}
