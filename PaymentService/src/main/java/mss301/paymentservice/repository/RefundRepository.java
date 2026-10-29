package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.paymentservice.entity.Refund;
import mss301.paymentservice.enums.RefundStatus;
import mss301.paymentservice.util.LockHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID>, JpaSpecificationExecutor<Refund> {

    List<Refund> findAllByIdempotencyKey(String idempotencyKey);

    List<Refund> findAllByBookingIdOrderByCreatedAtAsc(UUID bookingId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    // Tiền đang giữ chỗ trên payment (chưa chuyển xong): REFUND_PENDING + FAILED (FAILED vẫn còn nợ khách, admin thử lại).
    // Bỏ qua hoàn của phiên trả trễ / trùng (attemptId khác null): tiền đó không nằm trong payment.amount.
    @Query("""
            select coalesce(sum(r.amount), 0) from Refund r
            where r.paymentId = :paymentId and r.status in :statuses and r.attemptId is null
            """)
    BigDecimal sumAmount(@Param("paymentId") UUID paymentId, @Param("statuses") Collection<RefundStatus> statuses);

    // Như sumAmount nhưng cho cả trang payment một lần (tránh N+1 khi liệt kê): [paymentId, tổng].
    @Query("""
            select r.paymentId, sum(r.amount) from Refund r
            where r.paymentId in :paymentIds and r.status in :statuses and r.attemptId is null
            group by r.paymentId
            """)
    List<Object[]> sumAmountByPayment(@Param("paymentIds") Collection<UUID> paymentIds,
                                      @Param("statuses") Collection<RefundStatus> statuses);

    @Query("""
            select r.id from Refund r
            where r.status = :status and r.nextAttemptAt <= :now
            order by r.nextAttemptAt
            """)
    List<UUID> findDueIds(@Param("status") RefundStatus status, @Param("now") Instant now, Pageable pageable);

    // Dispatcher "nhận" việc: dòng đang được instance khác xử lý thì bỏ qua ngay (SKIP LOCKED), không đứng chờ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = LockHints.LOCK_TIMEOUT, value = LockHints.SKIP_LOCKED))
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findForUpdateSkipLocked(@Param("id") UUID id);

    // Admin thử lại: chờ khóa (không bỏ qua) để không đổi trạng thái giữa lúc dispatcher ghi kết quả.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :id")
    Optional<Refund> findForUpdate(@Param("id") UUID id);
}
