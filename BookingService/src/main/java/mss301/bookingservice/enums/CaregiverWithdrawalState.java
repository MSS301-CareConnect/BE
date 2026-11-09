package mss301.bookingservice.enums;

// Trạng thái xử lý một yêu cầu hủy buổi — không lưu DB, suy ra từ buổi (CaregiverWithdrawalMapper.resolveState):
// PENDING = buổi chưa bắt đầu, vẫn của caregiver xin hủy (trung tâm chưa phân người khác); REASSIGNED = buổi đã sang người
// khác; CLOSED = buổi đã kết thúc / bị hủy / quá giờ mà chưa đổi người.
public enum CaregiverWithdrawalState {
    PENDING,
    REASSIGNED,
    CLOSED
}
