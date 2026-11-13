package mss301.bookingservice.repository.projection;

import java.util.UUID;

// Số buổi đã hoàn thành của một caregiver theo từng dịch vụ.
public interface CaregiverServiceCountView {
    UUID getCaregiverId();
    UUID getCareServiceId();
    String getServiceName();
    Long getCompletedVisits();
}
