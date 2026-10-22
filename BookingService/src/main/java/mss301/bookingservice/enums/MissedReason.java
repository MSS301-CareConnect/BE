package mss301.bookingservice.enums;

// Vì sao buổi thành MISSED (OPS-06) — đồng bộ CHECK ck_booking_occurrences_missed_reason. CAREGIVER: không check-in
// đúng hạn hoặc manager bác báo vắng → khách được hoàn 100% cọc buổi.
public enum MissedReason {
    CAREGIVER
}
