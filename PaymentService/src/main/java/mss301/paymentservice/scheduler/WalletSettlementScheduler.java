package mss301.paymentservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.service.WalletSettlementService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class WalletSettlementScheduler {
    WalletSettlementService walletSettlementService;

    // Định kỳ quyết toán tiền vào ví cho các buổi đã hoàn thành.
    @Scheduled(fixedDelayString = "${payment.wallet.settlement-scan-interval:10m}",
            initialDelayString = "${payment.wallet.settlement-scan-interval:10m}")
    public void settleCompletedOccurrences() {
        List<UUID> bookingIds = walletSettlementService.findDueBookingIds();
        for (UUID bookingId : bookingIds) {
            try {
                walletSettlementService.process(bookingId);
            } catch (RuntimeException e) {
                log.error("Wallet settlement crashed for booking {} ({})", bookingId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
