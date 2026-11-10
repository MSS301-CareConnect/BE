package mss301.bookingservice.repository;

import mss301.bookingservice.entity.Complaint;
import mss301.bookingservice.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, UUID>, JpaSpecificationExecutor<Complaint> {
    Optional<Complaint> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByComplaintCode(String complaintCode);

    // D20: một khiếu nại đang mở cho mỗi buổi; khiếu nại cấp booking (không gắn buổi) tính riêng theo booking.
    boolean existsByOccurrenceIdAndStatusInAndDeletedAtIsNull(UUID occurrenceId, Collection<ComplaintStatus> statuses);

    boolean existsByBookingIdAndOccurrenceIdIsNullAndStatusInAndDeletedAtIsNull(UUID bookingId,
            Collection<ComplaintStatus> statuses);

    // Trạng thái khiếu nại và audit cùng commit; không có hai quyết định admin trên cùng khiếu nại.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Complaint c where c.id = :id and c.deletedAt is null")
    Optional<Complaint> findForUpdate(@Param("id") UUID id);
}
