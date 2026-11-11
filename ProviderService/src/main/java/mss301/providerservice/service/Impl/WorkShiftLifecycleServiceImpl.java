package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.repository.ShiftRegistrationRepository;
import mss301.providerservice.repository.WorkShiftRepository;
import mss301.providerservice.service.WorkShiftLifecycleService;
import mss301.providerservice.util.ProviderTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WorkShiftLifecycleServiceImpl implements WorkShiftLifecycleService {

    static String STARTED_PENDING_REASON = "Ca đã bắt đầu trước khi đăng ký được duyệt";

    WorkShiftRepository workShiftRepository;
    ShiftRegistrationRepository shiftRegistrationRepository;
    Clock clock;

    // Cập nhật trạng thái lưu của ca theo thời gian thực (đóng / hoàn thành).
    @Override
    @Transactional
    public void advance() {
        Instant now = clock.instant();
        // Giờ ca lưu theo giờ Việt Nam (work_date + time) → so với "bây giờ" theo cùng múi giờ.
        LocalDateTime local = LocalDateTime.ofInstant(now, ProviderTime.ZONE);
        int completed = workShiftRepository.completeEnded(local.toLocalDate(), local.toLocalTime(), now);
        int closed = workShiftRepository.closeExpiredOpen(local.toLocalDate(), local.toLocalTime(), now);
        int rejected = shiftRegistrationRepository.rejectPendingOfStartedShifts(local.toLocalDate(),
                local.toLocalTime(), now, STARTED_PENDING_REASON);
        if (completed + closed + rejected > 0) {
            log.info("Work shift lifecycle: {} completed, {} closed, {} pending registrations rejected",
                    completed, closed, rejected);
        }
    }
}
