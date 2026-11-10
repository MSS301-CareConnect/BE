package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import mss301.paymentservice.entity.RemainingInvoice;
import mss301.paymentservice.enums.InvoiceStatus;
import mss301.paymentservice.util.LockHints;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RemainingInvoiceRepository extends JpaRepository<RemainingInvoice, UUID>,
        JpaSpecificationExecutor<RemainingInvoice> {

    Optional<RemainingInvoice> findByBookingId(UUID bookingId);

    List<RemainingInvoice> findAllByCustomerIdAndStatusOrderByDueAtAsc(UUID customerId, InvoiceStatus status);

    // Khóa hóa đơn khi thanh toán / miễn / giảm: không hai thao tác cùng đổi số tiền hoặc trạng thái.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RemainingInvoice i where i.id = :id")
    Optional<RemainingInvoice> findForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from RemainingInvoice i where i.bookingId = :bookingId")
    Optional<RemainingInvoice> findForUpdateByBookingId(@Param("bookingId") UUID bookingId);

    // Job OVERDUE: dòng đang bị thao tác khác khóa thì bỏ qua lượt này.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = LockHints.LOCK_TIMEOUT, value = LockHints.SKIP_LOCKED))
    @Query("select i from RemainingInvoice i where i.id = :id")
    Optional<RemainingInvoice> findForUpdateSkipLocked(@Param("id") UUID id);

    @Query("select i.id from RemainingInvoice i where i.status = :status and i.dueAt <= :now order by i.dueAt")
    List<UUID> findDueIds(@Param("status") InvoiceStatus status, @Param("now") Instant now, Pageable pageable);
}
