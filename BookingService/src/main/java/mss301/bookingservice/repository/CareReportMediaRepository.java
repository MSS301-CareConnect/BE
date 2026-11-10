package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CareReportMedia;
import mss301.bookingservice.entity.CareReportMediaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CareReportMediaRepository extends JpaRepository<CareReportMedia, CareReportMediaId> {
    List<CareReportMedia> findByIdReportIdOrderByDisplayOrder(UUID reportId);
}
