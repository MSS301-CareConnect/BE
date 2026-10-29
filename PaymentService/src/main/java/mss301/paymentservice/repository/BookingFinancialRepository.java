package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.entity.BookingFinancial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingFinancialRepository extends JpaRepository<BookingFinancial, UUID> {

    // Khóa tài chính của booking: mọi lệnh tiền của một booking (cọc, hoàn, hóa đơn) chạy tuần tự, bất biến
    // "đã hoàn + đang chờ ≤ đã thu" kiểm được trong transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from BookingFinancial f where f.bookingId = :bookingId")
    Optional<BookingFinancial> findForUpdate(@Param("bookingId") UUID bookingId);
}
