package mss301.paymentservice.repository;

import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.BookingFinancialStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OccurrenceFinancialRepository extends JpaRepository<OccurrenceFinancial, UUID> {

    List<OccurrenceFinancial> findAllByBookingId(UUID bookingId);

    // Ví (S1): booking đã trả khoản đầu còn buổi đã bắt đầu mà chưa chốt (chưa trạng thái cuối, chưa cộng cho trung tâm).
    @Query("""
            select distinct o.bookingId from OccurrenceFinancial o, BookingFinancial f
            where f.bookingId = o.bookingId and f.status <> :unpaidStatus
              and o.finalStatus is null and o.settledAt is null and o.startAt <= :cutoff
            """)
    List<UUID> findSettlementCandidateBookingIds(@Param("unpaidStatus") BookingFinancialStatus unpaidStatus,
                                                 @Param("cutoff") Instant cutoff,
                                                 Pageable pageable);
}
