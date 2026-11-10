package mss301.paymentservice.repository;

import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface DepositForfeitureRepository extends JpaRepository<DepositForfeiture, UUID> {

    // Lệnh hoàn gửi lại cùng key không ghi trùng phần bị giữ của một buổi (kèm unique index ở DB).
    boolean existsByIdempotencyKeyAndOccurrenceId(String idempotencyKey, UUID occurrenceId);

    // Doanh thu (cùng mốc paid_at của khoản đầu như đã thu / đã hoàn) — phần bị giữ thuộc một bên hưởng.
    @Query("""
            select coalesce(sum(f.amount), 0) from DepositForfeiture f, Payment p
            where p.id = f.paymentId and p.status = :status and f.beneficiary = :beneficiary
              and p.paidAt >= :from and p.paidAt <= :to
              and (:centerId is null or f.centerId = :centerId)
            """)
    BigDecimal sumForRevenue(@Param("beneficiary") ForfeitureBeneficiary beneficiary,
                             @Param("status") PaymentStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("centerId") UUID centerId);

    // Danh sách theo lúc ghi: caregiver chỉ thấy phần mình hưởng; trung tâm / admin thấy cả hai bên.
    @Query("""
            select f from DepositForfeiture f
            where f.createdAt >= :from and f.createdAt <= :to
              and (:centerId is null or f.centerId = :centerId)
              and (:caregiverUserId is null or f.caregiverUserId = :caregiverUserId)
              and (:beneficiary is null or f.beneficiary = :beneficiary)
            order by f.createdAt desc
            """)
    Page<DepositForfeiture> findPage(@Param("centerId") UUID centerId,
                                     @Param("caregiverUserId") UUID caregiverUserId,
                                     @Param("beneficiary") ForfeitureBeneficiary beneficiary,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Pageable pageable);

    @Query("""
            select coalesce(sum(f.amount), 0) from DepositForfeiture f
            where f.createdAt >= :from and f.createdAt <= :to
              and (:centerId is null or f.centerId = :centerId)
              and (:caregiverUserId is null or f.caregiverUserId = :caregiverUserId)
              and f.beneficiary = :beneficiary
            """)
    BigDecimal sumByBeneficiary(@Param("centerId") UUID centerId,
                                @Param("caregiverUserId") UUID caregiverUserId,
                                @Param("beneficiary") ForfeitureBeneficiary beneficiary,
                                @Param("from") Instant from,
                                @Param("to") Instant to);
}
