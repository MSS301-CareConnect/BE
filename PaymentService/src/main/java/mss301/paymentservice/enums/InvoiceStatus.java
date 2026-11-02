package mss301.paymentservice.enums;

// Hóa đơn phần còn lại: PENDING → PAID | OVERDUE | WAIVED | EXPIRED | CANCELLED; OVERDUE → PAID | WAIVED — CHECK
// remaining_invoices.status. OVERDUE (= khách nợ) chỉ cho hóa đơn sau hoàn tất của booking cũ; hóa đơn trước buổi đầu
// (spec v1 §9.1) quá hạn thành EXPIRED (booking hết hạn, không phải nợ), hết buổi phải trả thì CANCELLED.
public enum InvoiceStatus {
    PENDING,
    PAID,
    OVERDUE,
    WAIVED,
    EXPIRED,
    CANCELLED
}
