package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingAction;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.service.BookingExpiryService;
import mss301.bookingservice.service.BookingLifecycleService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookingExpiryServiceImpl implements BookingExpiryService {

    BookingRepository bookingRepository;
    BookingLifecycleService bookingLifecycleService;
    BookingProperties bookingProperties;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findExpirableIds() {
        return bookingRepository.findExpirableIds(BookingStatus.PENDING, BookingStatus.AWAITING_DEPOSIT, clock.instant(),
                PageRequest.of(0, bookingProperties.getSchedule().getExpiryBatchSize()));
    }

    @Override
    @Transactional
    public void expire(UUID id) {
        Optional<Booking> lockedBooking = bookingRepository.findForUpdate(id);
        // Trong lúc chờ khóa, booking có thể vừa được duyệt / đặt cọc / hủy → kiểm lại, không đúng thì bỏ qua.
        if (lockedBooking.isEmpty() || !isOverdue(lockedBooking.get(), clock.instant())) {
            return;
        }
        bookingLifecycleService.apply(lockedBooking.get(), BookingAction.EXPIRE, null, null);
    }

    private boolean isOverdue(Booking booking, Instant now) {
        return switch (booking.getStatus()) {
            case PENDING -> booking.getAcceptanceDueAt() != null && !now.isBefore(booking.getAcceptanceDueAt());
            case AWAITING_DEPOSIT -> booking.getDepositDueAt() != null && !now.isBefore(booking.getDepositDueAt());
            default -> false;
        };
    }
}
