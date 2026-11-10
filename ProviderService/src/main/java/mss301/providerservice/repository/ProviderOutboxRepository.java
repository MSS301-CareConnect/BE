package mss301.providerservice.repository;

import mss301.providerservice.entity.ProviderOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
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

    // Sự kiện khóa / mở trung tâm đi theo thứ tự: còn SUSPEND / RESUME trước chưa SENT / SKIPPED thì chờ.
    // H-08: admin bỏ qua (SKIPPED) sự kiện FAILED đã xử lý tay để gỡ chặn các sự kiện sau.
    @Query("""
            select count(o) > 0 from ProviderOutbox o
            where o.centerId = :centerId and o.sequenceNo < :sequenceNo and o.eventType in ('SUSPEND', 'RESUME')
              and o.status not in ('SENT', 'SKIPPED') and o.deletedAt is null
            """)
    boolean existsBlockingPredecessor(@Param("centerId") UUID centerId, @Param("sequenceNo") Long sequenceNo);

    // H-08: màn hình sự cố admin.
    Page<ProviderOutbox> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    Page<ProviderOutbox> findByDeletedAtIsNull(Pageable pageable);
}
