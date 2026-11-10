package mss301.paymentservice.repository;

import mss301.paymentservice.dto.response.WalletTypeTotalResponse;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    // Kiểm trùng sau khi đã khóa ví (kèm unique index uk_wallet_transactions_reference ở DB).
    boolean existsByWalletIdAndReferenceKey(UUID walletId, String referenceKey);

    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId and t.createdAt >= :from and t.createdAt <= :to
              and (:type is null or t.type = :type)
            order by t.createdAt desc
            """)
    Page<WalletTransaction> findPage(@Param("walletId") UUID walletId,
                                     @Param("type") WalletTransactionType type,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    @Query("""
            select new mss301.paymentservice.dto.response.WalletTypeTotalResponse(t.type, count(t),
                   coalesce(sum(t.amount), 0))
            from WalletTransaction t
            where t.walletId = :walletId and t.createdAt >= :from and t.createdAt <= :to
            group by t.type
            """)
    List<WalletTypeTotalResponse> sumByType(@Param("walletId") UUID walletId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    // Chuỗi thời gian của dashboard: service gom theo ngày / tháng giờ Việt Nam.
    @Query("""
            select t from WalletTransaction t
            where t.walletId = :walletId and t.createdAt >= :from and t.createdAt <= :to and t.type in :types
            order by t.createdAt
            """)
    List<WalletTransaction> findAllForSeries(@Param("walletId") UUID walletId,
                                             @Param("types") Collection<WalletTransactionType> types,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    @Query("""
            select count(distinct t.bookingId) from WalletTransaction t
            where t.walletId = :walletId and t.createdAt >= :from and t.createdAt <= :to and t.type in :types
              and t.bookingId is not null
            """)
    long countBookings(@Param("walletId") UUID walletId,
                       @Param("types") Collection<WalletTransactionType> types,
                       @Param("from") Instant from,
                       @Param("to") Instant to);

    // Tổng đã cộng / trừ của một loại cho một booking trong một ví (trần trừ ví trung tâm khi hoàn khiếu nại).
    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t
            where t.walletId = :walletId and t.bookingId = :bookingId and t.type = :type
            """)
    BigDecimal sumByBooking(@Param("walletId") UUID walletId,
                            @Param("bookingId") UUID bookingId,
                            @Param("type") WalletTransactionType type);

    // Hoa hồng nền tảng thu trên các buổi của một trung tâm (dashboard trung tâm thấy phần bị trừ).
    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t
            where t.centerId = :centerId and t.type = :type and t.createdAt >= :from and t.createdAt <= :to
            """)
    BigDecimal sumByCenter(@Param("centerId") UUID centerId,
                           @Param("type") WalletTransactionType type,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    // Admin: tổng một loại biến động trên mọi ví của một loại chủ (vd tiền hoàn vào ví khách).
    @Query("""
            select coalesce(sum(t.amount), 0) from WalletTransaction t, Wallet w
            where w.id = t.walletId and w.ownerType = :ownerType and t.type = :type
              and t.createdAt >= :from and t.createdAt <= :to
            """)
    BigDecimal sumByOwnerType(@Param("ownerType") WalletOwnerType ownerType,
                              @Param("type") WalletTransactionType type,
                              @Param("from") Instant from,
                              @Param("to") Instant to);
}
