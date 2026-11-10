package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.BookingClient;
import mss301.providerservice.client.dto.BusyCaregiverClientRequest;
import mss301.providerservice.client.dto.BusyCaregiverClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.BookingClientMapper;
import mss301.providerservice.service.BookingAvailabilityService;
import mss301.providerservice.util.TimeWindow;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class BookingAvailabilityServiceImpl implements BookingAvailabilityService {

    // Giới hạn mỗi lần gọi của contract C5.1.
    static int MAX_CAREGIVERS_PER_CALL = 200;
    static int MAX_SLOTS_PER_CALL = 60;

    BookingClient bookingClient;
    BookingClientMapper bookingClientMapper;

    @Override
    public Set<UUID> findBusyCaregiverIds(Collection<UUID> caregiverIds, List<TimeWindow> slots) {
        Set<UUID> busyCaregiverIds = new LinkedHashSet<>();
        if (caregiverIds.isEmpty() || slots.isEmpty()) {
            return busyCaregiverIds;
        }
        List<UUID> distinctIds = new ArrayList<>(new LinkedHashSet<>(caregiverIds));
        for (List<UUID> caregiverBatch : partition(distinctIds, MAX_CAREGIVERS_PER_CALL)) {
            for (List<TimeWindow> slotBatch : partition(slots, MAX_SLOTS_PER_CALL)) {
                busyCaregiverIds.addAll(callBooking(caregiverBatch, slotBatch));
            }
        }
        return busyCaregiverIds;
    }

    private List<UUID> callBooking(List<UUID> caregiverIds, List<TimeWindow> slots) {
        BusyCaregiverClientRequest request = bookingClientMapper.toBusyCaregiverClientRequest(caregiverIds, slots);
        RemoteApiResponse<BusyCaregiverClientResponse> response = bookingClient.findBusyCaregivers(request);
        if (response == null || response.getResult() == null || response.getResult().getBusyCaregiverIds() == null) {
            log.warn("BookingService returned an empty busy-caregivers response");
            throw new AppException(ErrorCode.BOOKING_SERVICE_UNAVAILABLE);
        }
        return response.getResult().getBusyCaregiverIds();
    }

    private <T> List<List<T>> partition(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < items.size(); start += batchSize) {
            batches.add(items.subList(start, Math.min(start + batchSize, items.size())));
        }
        return batches;
    }
}
