package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BookingOccurrenceService {

    APIResponse<List<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                        OccurrenceStatus status);

    APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, UUID id);

    APIResponse<CaregiverOccurrenceResponse> getById(UUID id);
}
