package mss301.paymentservice.scheduler;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.service.RemainingInvoiceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Hóa đơn REMAINING quá hạn → OVERDUE (= khách đang nợ, Booking chặn đặt lịch mới). Mặc định 10 phút.
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RemainingInvoiceOverdueScheduler {
    RemainingInvoiceService remainingInvoiceService;

    @Scheduled(fixedDelayString = "${payment.invoice.overdue-scan-interval:10m}",
            initialDelayString = "${payment.invoice.overdue-scan-interval:10m}")
    public void markOverdueInvoices() {
        List<UUID> invoiceIds = remainingInvoiceService.findOverdueCandidateIds();
        for (UUID invoiceId : invoiceIds) {
            try {
                remainingInvoiceService.markOverdue(invoiceId);
            } catch (RuntimeException e) {
                log.error("Overdue marking crashed for invoice {} ({})", invoiceId, e.getClass().getSimpleName(), e);
            }
        }
    }
}
