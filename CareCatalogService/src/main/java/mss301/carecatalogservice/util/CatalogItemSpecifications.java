package mss301.carecatalogservice.util;

import jakarta.persistence.criteria.Predicate;
import mss301.carecatalogservice.entity.CatalogItem;
import mss301.carecatalogservice.enums.ServiceStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Filter động cho danh mục dịch vụ chuẩn (JPA Specification — tránh tham số nullable trong JPQL). Tham số null = bỏ qua.
 */
public final class CatalogItemSpecifications {

    private CatalogItemSpecifications() {
    }

    public static Specification<CatalogItem> filter(ServiceStatus status, String category, String keyword) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(builder.equal(builder.lower(root.get("category")), category.strip().toLowerCase(Locale.ROOT)));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("name")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
