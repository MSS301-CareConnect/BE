package mss301.providerservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.service.CertificateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateAiCheckScheduler {
    CertificateService certificateService;

    // Định kỳ chạy AI kiểm tra các chứng chỉ tới hạn; lỗi một chứng chỉ không dừng cả lượt.
    @Scheduled(fixedDelayString = "${PROVIDER_AI_CHECK_SCAN_INTERVAL:15s}",
            initialDelayString = "${PROVIDER_AI_CHECK_INITIAL_DELAY:30s}")
    public void process() {
        for (UUID id : certificateService.findDueAiCheckIds()) {
            try {
                certificateService.processAiCheck(id);
            } catch (RuntimeException exception) {
                log.error("Không xử lý được kiểm tra AI của chứng chỉ {}", id, exception);
            }
        }
    }
}
