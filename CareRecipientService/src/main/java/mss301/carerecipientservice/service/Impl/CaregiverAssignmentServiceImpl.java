package mss301.carerecipientservice.service.Impl;

import mss301.carerecipientservice.service.CaregiverAssignmentService;
import org.springframework.stereotype.Service;

import java.util.UUID;

// TODO(BookingService): thay bằng lời gọi API nội bộ của Booking. Tạm thời từ chối mọi caregiver (fail-closed).
@Service
public class CaregiverAssignmentServiceImpl implements CaregiverAssignmentService {

    @Override
    public boolean isAssigned(UUID recipientId, UUID caregiverId) {
        return false;
    }
}
