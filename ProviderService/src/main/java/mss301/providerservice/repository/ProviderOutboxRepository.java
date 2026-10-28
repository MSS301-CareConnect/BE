package mss301.providerservice.repository;

import mss301.providerservice.entity.ProviderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderOutboxRepository extends JpaRepository<ProviderOutbox, UUID> {
    @Query("select o.id from ProviderOutbox o where o.status = :status and o.nextAttemptAt <= :now and o.deletedAt is null order by o.createdAt")
    List<UUID> findDueIds(@Param("status") String status, @Param("now") Instant now, Pageable pageable);

    // Một worker nhận mỗi dòng; lease giữ công việc trong lúc gọi mạng ngoài transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProviderOutbox o where o.id = :id and o.deletedAt is null")
    Optional<ProviderOutbox> findForUpdate(@Param("id") UUID id);

    boolean existsByCenterIdAndSequenceNoLessThanAndStatusNotAndEventTypeNotAndDeletedAtIsNull(
            UUID centerId, Long sequenceNo, String status, String eventType);
}
