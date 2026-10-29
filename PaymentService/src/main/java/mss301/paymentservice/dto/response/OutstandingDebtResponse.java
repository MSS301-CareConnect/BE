package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

// Booking chỉ đọc hasDebt (= có hóa đơn OVERDUE) để chặn đặt lịch mới (C2.6).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutstandingDebtResponse {
    boolean hasDebt;

    List<RemainingInvoiceResponse> overdueInvoices;

    List<RemainingInvoiceResponse> pendingInvoices;
}
