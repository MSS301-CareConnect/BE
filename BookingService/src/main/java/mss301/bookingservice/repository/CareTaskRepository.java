package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CareTask;
import mss301.bookingservice.enums.CareTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import mss301.bookingservice.enums.CareTaskType;
import java.util.UUID;

@Repository
public interface CareTaskRepository extends JpaRepository<CareTask, UUID> {

    List<CareTask> findBySessionIdAndDeletedAtIsNullOrderBySortOrderAsc(UUID sessionId);

    long countBySessionIdAndTaskTypeAndDeletedAtIsNull(UUID sessionId, CareTaskType taskType);

    Optional<CareTask> findByIdAndSessionIdAndDeletedAtIsNull(UUID id, UUID sessionId);

    boolean existsBySessionIdAndStatusAndDeletedAtIsNull(UUID sessionId, CareTaskStatus status);
}
