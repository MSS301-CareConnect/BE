package mss301.providerservice.mapper;

import mss301.providerservice.client.dto.BusyCaregiverClientRequest;
import mss301.providerservice.client.dto.TimeSlotClientRequest;
import mss301.providerservice.util.TimeWindow;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.UUID;

// Request gửi BookingService (C5.1) — một lô caregiver + một lô khung giờ do service chia sẵn.
@Mapper(config = MapStructConfig.class)
public interface BookingClientMapper {

    TimeSlotClientRequest toTimeSlotClientRequest(TimeWindow timeWindow);

    BusyCaregiverClientRequest toBusyCaregiverClientRequest(List<UUID> caregiverIds, List<TimeWindow> slots);
}
