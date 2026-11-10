package mss301.providerservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.service.CertificateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bước 4 của luồng duyệt chứng chỉ: gửi chứng chỉ trung tâm vừa xác nhận (ai_check_status = QUEUED) sang AIService
 * đọc ảnh và lưu kết quả. Chạy nền để Center Manager không phải chờ AI; lỗi thì thử lại với backoff, hết lượt → FAILED.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateAiCheckScheduler {
    CertificateService certificateService;

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
