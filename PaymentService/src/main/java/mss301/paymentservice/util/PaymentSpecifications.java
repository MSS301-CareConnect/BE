package mss301.paymentservice.util;

import jakarta.persistence.criteria.Predicate;
import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.entity.WalletTransaction;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.WalletTransactionType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PaymentSpecifications {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private PaymentSpecifications() {
    }

    // Giao dịch của một ví trong [from, to]; type null = mọi loại.
    public static Specification<WalletTransaction> walletTransactions(UUID walletId, WalletTransactionType type,
                                                                      Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("walletId"), walletId));
            predicates.add(criteriaBuilder.between(root.get("createdAt"), from, to));
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Danh sách theo lúc ghi: caregiver chỉ thấy phần mình hưởng; trung tâm / admin thấy cả hai bên.
    public static Specification<DepositForfeiture> forfeitures(UUID centerId, UUID caregiverUserId,
                                                               ForfeitureBeneficiary beneficiary,
                                                               Instant from, Instant to) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.between(root.get("createdAt"), from, to));
            if (centerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("centerId"), centerId));
            }
            if (caregiverUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("caregiverUserId"), caregiverUserId));
            }
            if (beneficiary != null) {
                predicates.add(criteriaBuilder.equal(root.get("beneficiary"), beneficiary));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
