package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingDetailRepository extends JpaRepository<BookingDetail, UUID> {

    @Query("select d from BookingDetail d where d.booking.id = :bookingId order by d.createdAt, d.careServiceId")
    List<BookingDetail> findAllByBooking(@Param("bookingId") UUID bookingId);
}
