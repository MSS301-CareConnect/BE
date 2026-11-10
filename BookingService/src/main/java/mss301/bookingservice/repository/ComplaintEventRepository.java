package mss301.bookingservice.repository;

import mss301.bookingservice.entity.ComplaintEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintEventRepository extends JpaRepository<ComplaintEvent, UUID> {
    List<ComplaintEvent> findByComplaintIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID complaintId);
}
