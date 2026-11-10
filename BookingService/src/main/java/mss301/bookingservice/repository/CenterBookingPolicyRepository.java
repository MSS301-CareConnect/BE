package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CenterBookingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CenterBookingPolicyRepository extends JpaRepository<CenterBookingPolicy, UUID> {

    Optional<CenterBookingPolicy> findByCenterIdAndDeletedAtIsNull(UUID centerId);
}
