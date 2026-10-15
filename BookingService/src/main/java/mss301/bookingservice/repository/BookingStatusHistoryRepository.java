package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {

    @Query("select h from BookingStatusHistory h where h.booking.id = :bookingId order by h.changedAt, h.id")
    List<BookingStatusHistory> findAllByBooking(@Param("bookingId") UUID bookingId);
}
