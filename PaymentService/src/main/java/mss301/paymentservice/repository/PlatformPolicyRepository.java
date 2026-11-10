package mss301.paymentservice.repository;

import mss301.paymentservice.entity.PlatformPolicy;
import mss301.paymentservice.enums.PolicyScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlatformPolicyRepository extends JpaRepository<PlatformPolicy, UUID> {

    Optional<PlatformPolicy> findFirstByScopeAndCenterIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
            PolicyScope scope, UUID centerId, Instant at);

    Optional<PlatformPolicy> findFirstByScopeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(PolicyScope scope,
                                                                                                  Instant at);
}
