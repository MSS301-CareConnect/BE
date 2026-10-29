package mss301.paymentservice.repository;

import mss301.paymentservice.entity.OccurrenceFinancial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OccurrenceFinancialRepository extends JpaRepository<OccurrenceFinancial, UUID> {

    List<OccurrenceFinancial> findAllByBookingId(UUID bookingId);
}
