package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CaregiverChangeRequest;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CaregiverChangeRequestRepository extends JpaRepository<CaregiverChangeRequest, UUID> {

    List<CaregiverChangeRequest> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    @Query("""
        SELECT r FROM CaregiverChangeRequest r
        WHERE r.centerId = :centerId
          AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
    """)
    Page<CaregiverChangeRequest> findByCenterIdAndStatus(
            @Param("centerId") UUID centerId,
            @Param("status") CaregiverChangeRequestStatus status,
            Pageable pageable
    );

    long countByBookingIdAndStatus(UUID bookingId, CaregiverChangeRequestStatus status);

    long countByBookingId(UUID bookingId);
}
