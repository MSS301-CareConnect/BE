package mss301.paymentservice.enums;

// Phạm vi danh sách / thống kê yêu cầu rút tiền (2026-11-10):
// OWN — yêu cầu của chính ví người gọi (ADMIN: mọi yêu cầu, như trước);
// REVIEW — hàng đợi người gọi phải xử lý: CENTER_MANAGER → yêu cầu của khách / caregiver gắn trung tâm mình,
// ADMIN → yêu cầu của ví trung tâm và yêu cầu không gắn trung tâm (cũ).
public enum WalletWithdrawalScope {
    OWN,
    REVIEW
}
