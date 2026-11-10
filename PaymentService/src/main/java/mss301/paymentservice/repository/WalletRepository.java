package mss301.paymentservice.repository;

import jakarta.persistence.LockModeType;
import mss301.paymentservice.dto.response.WalletBalanceTotalResponse;
import mss301.paymentservice.entity.Wallet;
import mss301.paymentservice.enums.WalletOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByOwnerTypeAndOwnerId(WalletOwnerType ownerType, UUID ownerId);

    // Khóa ví khi đổi số dư: hai biến động song song (hoàn tiền, giữ cọc, rút tiền) không ghi đè nhau.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.ownerType = :ownerType and w.ownerId = :ownerId")
    Optional<Wallet> findForUpdate(@Param("ownerType") WalletOwnerType ownerType, @Param("ownerId") UUID ownerId);

    // Khóa theo id (duyệt / từ chối / hoàn tất rút tiền) — cùng lý do như trên.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findForUpdateById(@Param("id") UUID id);

    // Admin: tổng tiền nền tảng đang giữ hộ theo loại chủ ví.
    @Query("""
            select new mss301.paymentservice.dto.response.WalletBalanceTotalResponse(w.ownerType, count(w),
                   coalesce(sum(w.availableBalance), 0), coalesce(sum(w.heldBalance), 0))
            from Wallet w group by w.ownerType
            """)
    List<WalletBalanceTotalResponse> sumBalancesByOwnerType();
}
