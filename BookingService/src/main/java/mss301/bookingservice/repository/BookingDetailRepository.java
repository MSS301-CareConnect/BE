package mss301.bookingservice.repository;

import mss301.bookingservice.entity.BookingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingDetailRepository extends JpaRepository<BookingDetail, UUID> {

    List<BookingDetail> findByBookingIdOrderByCreatedAtAscCareServiceIdAsc(UUID bookingId);

    // Danh sách booking: tên dịch vụ của cả trang trong một truy vấn.
    List<BookingDetail> findByBookingIdInOrderByCreatedAtAscCareServiceIdAsc(Collection<UUID> bookingIds);
}
