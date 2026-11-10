package mss301.paymentservice.enums;

// Chủ ví — đồng bộ CHECK wallets.owner_type. CENTER: một ví / trung tâm (mọi manager dùng chung); PLATFORM: ví hoa hồng
// của nền tảng (ADMIN chỉ xem, không rút — tiền đã nằm ở tài khoản merchant).
public enum WalletOwnerType {
    CUSTOMER,
    CAREGIVER,
    CENTER,
    PLATFORM
}
