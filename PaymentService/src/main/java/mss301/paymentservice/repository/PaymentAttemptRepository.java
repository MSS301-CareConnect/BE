package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findByReference(String reference);

    Optional<PaymentAttempt> findByProviderCheckoutId(String providerCheckoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a join fetch a.payment where a.reference=:reference")
    Optional<PaymentAttempt> findByReferenceForUpdate(@Param("reference") String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttempt a join fetch a.payment where a.providerCheckoutId=:id")
    Optional<PaymentAttempt> findByCheckoutIdForUpdate(@Param("id") String id);
}
