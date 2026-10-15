package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.ScheduleConflictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Một lần truy vấn cho cả danh sách buổi: lấy buổi giao với khoảng bao [buổi đầu, buổi cuối] rồi lọc giao chính xác.
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ScheduleConflictServiceImpl implements ScheduleConflictService {

    static String CAREGIVER_LOCK_PREFIX = "booking:caregiver:";
    static String RECIPIENT_LOCK_PREFIX = "booking:recipient:";

    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID caregiverId, UUID recipientId) {
        if (caregiverId != null) {
            bookingRepository.lockScheduleKey(CAREGIVER_LOCK_PREFIX + caregiverId);
        }
        if (recipientId != null) {
            bookingRepository.lockScheduleKey(RECIPIENT_LOCK_PREFIX + recipientId);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkCaregiverAvailable(UUID caregiverId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (caregiverId == null || slots.isEmpty()) {
            return;
        }
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findCaregiverOverlaps(caregiverId,
                findRangeStart(slots), findRangeEnd(slots), OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
        if (hasOverlap(candidates, slots, excludeBookingId)) {
            throw new AppException(ErrorCode.CAREGIVER_SCHEDULE_CONFLICT);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkRecipientAvailable(UUID recipientId, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        if (recipientId == null || slots.isEmpty()) {
            return;
        }
        List<BookingOccurrence> candidates = bookingOccurrenceRepository.findRecipientOverlaps(recipientId,
                findRangeStart(slots), findRangeEnd(slots), OccurrenceStatus.ACTIVE, BookingStatus.SCHEDULE_HOLDING);
        if (hasOverlap(candidates, slots, excludeBookingId)) {
            throw new AppException(ErrorCode.RECIPIENT_SCHEDULE_CONFLICT);
        }
    }

    // Khoảng nửa mở [start, end): buổi kết thúc đúng lúc buổi khác bắt đầu KHÔNG tính là trùng.
    static boolean overlaps(Instant firstStart, Instant firstEnd, Instant secondStart, Instant secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }

    private boolean hasOverlap(List<BookingOccurrence> candidates, List<OccurrenceSlot> slots, UUID excludeBookingId) {
        for (BookingOccurrence candidate : candidates) {
            if (excludeBookingId != null && excludeBookingId.equals(candidate.getBooking().getId())) {
                continue;
            }
            for (OccurrenceSlot slot : slots) {
                if (overlaps(candidate.getStartAt(), candidate.getEndAt(), slot.getStartAt(), slot.getEndAt())) {
                    return true;
                }
            }
        }
        return false;
    }

    private Instant findRangeStart(List<OccurrenceSlot> slots) {
        return slots.stream()
                .map(OccurrenceSlot::getStartAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    private Instant findRangeEnd(List<OccurrenceSlot> slots) {
        return slots.stream()
                .map(OccurrenceSlot::getEndAt)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }
}
