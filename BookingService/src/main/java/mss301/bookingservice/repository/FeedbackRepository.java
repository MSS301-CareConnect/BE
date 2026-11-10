package mss301.bookingservice.repository;

import mss301.bookingservice.entity.Feedback;
import mss301.bookingservice.enums.FeedbackVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, UUID>, JpaSpecificationExecutor<Feedback> {
    boolean existsByBookingIdAndCaregiverIdAndDeletedAtIsNull(UUID bookingId, UUID caregiverId);

    // Một phản hồi/đánh giá; manager và admin cập nhật tuần tự.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feedback f where f.id = :id and f.deletedAt is null")
    Optional<Feedback> findForUpdate(@Param("id") UUID id);

    @Query("select avg(f.rating) as ratingAverage, count(f) as ratingCount from Feedback f where f.caregiverId = :id and f.visibility = :visibility and f.deletedAt is null")
    FeedbackRatingView summarizeRating(@Param("id") UUID id, @Param("visibility") FeedbackVisibility visibility);
}
