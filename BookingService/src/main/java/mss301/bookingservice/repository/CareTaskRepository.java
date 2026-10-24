package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CareTask;
import mss301.bookingservice.enums.CareTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import mss301.bookingservice.enums.CareTaskType;
import java.util.UUID;

@Repository
public interface CareTaskRepository extends JpaRepository<CareTask, UUID> {

    @Query("select t from CareTask t where t.session.id = :sessionId and t.deletedAt is null order by t.sortOrder")
    List<CareTask> findAllBySession(@Param("sessionId") UUID sessionId);

    long countBySessionIdAndTaskTypeAndDeletedAtIsNull(UUID sessionId, CareTaskType taskType);

    Optional<CareTask> findByIdAndSessionIdAndDeletedAtIsNull(UUID id, UUID sessionId);

    boolean existsBySessionIdAndStatusAndDeletedAtIsNull(UUID sessionId, CareTaskStatus status);
}
