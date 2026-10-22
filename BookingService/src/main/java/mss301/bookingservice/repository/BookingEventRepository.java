package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingEventRepository extends JpaRepository<BookingEvent, UUID> {

    @Query("select e from BookingEvent e where e.booking.id = :bookingId order by e.createdAt, e.id")
    List<BookingEvent> findAllByBooking(@Param("bookingId") UUID bookingId);
}
