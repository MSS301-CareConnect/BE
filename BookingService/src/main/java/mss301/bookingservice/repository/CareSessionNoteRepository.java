package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CareSessionNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CareSessionNoteRepository extends JpaRepository<CareSessionNote, UUID> {
    Page<CareSessionNote> findBySessionIdAndDeletedAtIsNull(UUID sessionId, Pageable pageable);
}
