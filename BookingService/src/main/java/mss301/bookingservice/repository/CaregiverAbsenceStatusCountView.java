package mss301.bookingservice.repository;

import mss301.bookingservice.enums.CaregiverAbsenceStatus;

// Số lần vắng ca theo trạng thái kết luận (thống kê của trung tâm / một caregiver / toàn hệ thống).
public interface CaregiverAbsenceStatusCountView {

    CaregiverAbsenceStatus getStatus();

    long getTotal();
}
