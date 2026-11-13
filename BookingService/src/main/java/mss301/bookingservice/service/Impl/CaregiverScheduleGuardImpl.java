package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.CaregiverScheduleGuard;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CaregiverScheduleGuardImpl implements CaregiverScheduleGuard {

    // Cùng khóa với BookingServiceImpl.lockSchedule (CAREGIVER_LOCK_PREFIX).
    static String CAREGIVER_LOCK_PREFIX = "booking:caregiver:";

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingProperties bookingProperties;

    // Khóa lịch của caregiver (advisory lock) để tránh xếp trùng đồng thời.
    @Override
    public void lockCaregiver(UUID caregiverId) {
        bookingRepository.lockScheduleKey(CAREGIVER_LOCK_PREFIX + caregiverId);
    }

    // Báo lỗi nếu caregiver đã có buổi trùng các khung giờ.
    @Override
    public void checkCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        ErrorCode conflict = findConflict(caregiverId, slots, excludeBookingId);
        if (conflict != null) {
            throw new AppException(conflict);
        }
    }

    // Caregiver không có buổi trùng các khung giờ.
    @Override
    public boolean isCaregiverFree(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        return findConflict(caregiverId, slots, excludeBookingId) == null;
    }

    // Buổi nào của danh sách bị trùng / sát buổi khác của caregiver.
    @Override
    public Set<Instant> findBusyStarts(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (slots.isEmpty()) {
            return Set.of();
        }
        Duration travelBuffer = bookingProperties.getSchedule().getTravelBuffer();
        Instant rangeStart = slots.stream().map(OccurrenceSlot::getStartAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant rangeEnd = slots.stream().map(OccurrenceSlot::getEndAt).max(Comparator.naturalOrder()).orElseThrow();
        List<BookingOccurrence> held = bookingOccurrenceRepository.findCaregiverOverlaps(caregiverId,
                        rangeStart.minus(travelBuffer), rangeEnd.plus(travelBuffer), OccurrenceStatus.ACTIVE,
                        BookingStatus.SCHEDULE_HOLDING).stream()
                .filter(candidate -> !candidate.getBooking().getId().equals(excludeBookingId))
                .toList();
        Set<Instant> busy = new HashSet<>();
        for (OccurrenceSlot slot : slots) {
            boolean clash = held.stream().anyMatch(candidate -> candidate.getStartAt().minus(travelBuffer).isBefore(slot.getEndAt())
                    && slot.getStartAt().isBefore(candidate.getEndAt().plus(travelBuffer)));
            if (clash) {
                busy.add(slot.getStartAt());
            }
        }
        return busy;
    }

    // Khoảng nửa mở [start, end); buổi của cùng booking được nối liền nên bỏ qua.
    private ErrorCode findConflict(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (slots.isEmpty()) {
            return null;
        }
        Duration travelBuffer = bookingProperties.getSchedule().getTravelBuffer();
        Instant rangeStart = slots.stream().map(OccurrenceSlot::getStartAt).min(Comparator.naturalOrder()).orElseThrow();
        Instant rangeEnd = slots.stream().map(OccurrenceSlot::getEndAt).max(Comparator.naturalOrder()).orElseThrow();
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findCaregiverOverlaps(caregiverId,
                        rangeStart.minus(travelBuffer), rangeEnd.plus(travelBuffer), OccurrenceStatus.ACTIVE,
                        BookingStatus.SCHEDULE_HOLDING).stream()
                .filter(candidate -> !candidate.getBooking().getId().equals(excludeBookingId))
                .toList();
        ErrorCode conflict = null;
        for (BookingOccurrence candidate : candidates) {
            for (OccurrenceSlot slot : slots) {
                if (candidate.getStartAt().isBefore(slot.getEndAt()) && slot.getStartAt().isBefore(candidate.getEndAt())) {
                    return ErrorCode.CAREGIVER_SCHEDULE_CONFLICT;
                }
                if (candidate.getStartAt().minus(travelBuffer).isBefore(slot.getEndAt())
                        && slot.getStartAt().isBefore(candidate.getEndAt().plus(travelBuffer))) {
                    conflict = ErrorCode.CAREGIVER_TRAVEL_BUFFER_CONFLICT;
                }
            }
        }
        return conflict;
    }
}
