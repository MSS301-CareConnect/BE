package mss301.bookingservice.enums;

// Kết luận một lần vắng ca của caregiver (2026-11-10) — đồng bộ CHECK ck_caregiver_absences_status. PENDING: chờ trung tâm
// kết luận (caregiver có thể gửi giải trình); EXCUSED: có lý do chính đáng, không tính vi phạm; CONFIRMED: vi phạm.
public enum CaregiverAbsenceStatus {
    PENDING,
    EXCUSED,
    CONFIRMED
}
