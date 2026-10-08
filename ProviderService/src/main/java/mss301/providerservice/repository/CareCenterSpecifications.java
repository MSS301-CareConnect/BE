package mss301.providerservice.repository;

import jakarta.persistence.criteria.Predicate;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.enums.CenterStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CareCenterSpecifications {

    private CareCenterSpecifications() {
    }

    public static Specification<CareCenter> filter(CenterStatus status, String keyword, boolean includeDeleted) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("centerStatus"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                Predicate nameLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("centerName")), pattern);
                Predicate emailLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), pattern);
                predicates.add(criteriaBuilder.or(nameLike, emailLike));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
