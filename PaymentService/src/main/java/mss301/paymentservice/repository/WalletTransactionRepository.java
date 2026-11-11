package mss301.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import mss301.paymentservice.repository.projection.WalletCenterTypeTotalView;
import mss301.paymentservice.repository.projection.WalletBookingTotalView;
import mss301.paymentservice.dto.response.WalletTypeTotalResponse;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.WalletOwnerType;
import mss301.paymentservice.enums.WalletTransactionType;
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
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID>,
        JpaSpecificationExecutor<WalletTransaction> {

    // Kiểm trùng sau khi đã khóa ví (kèm unique index uk_wallet_transactions_reference ở DB).
    boolean existsByWalletIdAndReferenceKey(UUID walletId, String referenceKey);

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
    List<WalletTransaction> findByWalletIdAndTypeInAndCreatedAtBetweenOrderByCreatedAtAsc(
            UUID walletId, Collection<WalletTransactionType> types, Instant from, Instant to);

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

    // 2026-11-10: số dư một ví theo trung tâm đã tạo ra khoản tiền (center_id của từng biến động) — chọn trung tâm xử lý
    // yêu cầu rút của khách / caregiver.
    @Query("""
            select t.centerId as centerId, w.ownerType as ownerType, t.type as type,
                   coalesce(sum(t.amount), 0) as totalAmount
            from WalletTransaction t, Wallet w
            where w.id = t.walletId and t.walletId = :walletId
            group by t.centerId, w.ownerType, t.type
            """)
    List<WalletCenterTypeTotalView> sumByCenterForWallet(@Param("walletId") UUID walletId);

    // 2026-11-10: tiền của khách / caregiver gắn một trung tâm còn trong ví (trung tâm đang giữ hộ).
    @Query("""
            select t.centerId as centerId, w.ownerType as ownerType, t.type as type,
                   coalesce(sum(t.amount), 0) as totalAmount
            from WalletTransaction t, Wallet w
            where w.id = t.walletId and t.centerId = :centerId and w.ownerType in :ownerTypes
            group by t.centerId, w.ownerType, t.type
            """)
    List<WalletCenterTypeTotalView> sumCustodyByCenter(@Param("centerId") UUID centerId,
                                                       @Param("ownerTypes") Collection<WalletOwnerType> ownerTypes);

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

    // 2026-11-11: quyết toán booking — tổng theo buổi / loại chủ ví / loại biến động.
    @Query("""
            select t.occurrenceId as occurrenceId, w.ownerType as ownerType, t.type as type,
                   coalesce(sum(t.amount), 0) as totalAmount
            from WalletTransaction t, Wallet w
            where w.id = t.walletId and t.bookingId = :bookingId
            group by t.occurrenceId, w.ownerType, t.type
            """)
    List<WalletBookingTotalView> sumByBookingForSettlement(@Param("bookingId") UUID bookingId);
}
