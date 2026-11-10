package mss301.bookingservice.repository;

import mss301.bookingservice.entity.CareReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareReportRepository extends JpaRepository<CareReport, UUID>, JpaSpecificationExecutor<CareReport> {
    Optional<CareReport> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsBySessionIdAndDeletedAtIsNull(UUID sessionId);

    // Submit và thay ảnh dùng cùng khóa report để không sửa báo cáo đã gửi.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CareReport r where r.id = :id and r.deletedAt is null")
    Optional<CareReport> findForUpdate(@Param("id") UUID id);
}
