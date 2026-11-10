package mss301.bookingservice.enums;

// Vì sao buổi bị ghi nhận caregiver vắng (2026-11-10) — đồng bộ CHECK ck_caregiver_absences_source. MISSED_CHECK_IN: job
// thấy quá hạn check-in mà không ai check-in; NO_SHOW_REPORT_REJECTED: trung tâm bác báo cáo "khách vắng" của caregiver.
public enum CaregiverAbsenceSource {
    MISSED_CHECK_IN,
    NO_SHOW_REPORT_REJECTED
}
