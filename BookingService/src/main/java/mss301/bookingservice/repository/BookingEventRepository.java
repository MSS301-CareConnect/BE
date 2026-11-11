package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

    List<BookingEvent> findByBookingIdOrderByCreatedAtAscIdAsc(UUID bookingId);
}
