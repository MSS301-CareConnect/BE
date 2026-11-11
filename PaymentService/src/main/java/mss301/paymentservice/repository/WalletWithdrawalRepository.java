package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.entity.WalletWithdrawal;
import mss301.paymentservice.enums.WalletWithdrawalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, UUID>,
        JpaSpecificationExecutor<WalletWithdrawal> {

    // Khóa yêu cầu khi admin đổi trạng thái: hai admin bấm cùng lúc không duyệt + từ chối cùng một yêu cầu.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WalletWithdrawal w where w.id = :id")
    Optional<WalletWithdrawal> findForUpdate(@Param("id") UUID id);

    long countByStatusIn(Collection<WalletWithdrawalStatus> statuses);

    @Query("select coalesce(sum(w.amount), 0) from WalletWithdrawal w where w.status in :statuses")
    BigDecimal sumAmountByStatusIn(@Param("statuses") Collection<WalletWithdrawalStatus> statuses);
}
