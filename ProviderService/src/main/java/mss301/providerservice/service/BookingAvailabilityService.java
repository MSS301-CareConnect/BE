package mss301.providerservice.service;

import mss301.providerservice.util.TimeWindow;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface BookingAvailabilityService {

    // Tìm caregiver đã bận booking trong các khung giờ cho trước.
    Set<UUID> findBusyCaregiverIds(Collection<UUID> caregiverIds, List<TimeWindow> slots);
}
