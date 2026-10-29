package mss301.paymentservice.enums;

// Hóa đơn phần còn lại: PENDING → PAID | OVERDUE | WAIVED; OVERDUE → PAID | WAIVED — CHECK remaining_invoices.status.
public enum InvoiceStatus {
    PENDING,
    PAID,
    OVERDUE,
    WAIVED
}
