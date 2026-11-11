package mss301.bookingservice.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import mss301.bookingservice.entity.CaregiverChangeRequest;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CaregiverChangeRequestRepository extends JpaRepository<CaregiverChangeRequest, UUID>,
        JpaSpecificationExecutor<CaregiverChangeRequest> {

    List<CaregiverChangeRequest> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    long countByBookingIdAndStatus(UUID bookingId, CaregiverChangeRequestStatus status);

    long countByBookingId(UUID bookingId);
}
