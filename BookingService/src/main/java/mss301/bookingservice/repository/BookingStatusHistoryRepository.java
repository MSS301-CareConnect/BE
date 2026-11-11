package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {

    List<BookingStatusHistory> findByBookingIdOrderByChangedAtAscIdAsc(UUID bookingId);
}
