package mss301.carerecipientservice.util;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filter động cho danh sách hồ sơ (JPA Specification — tránh tham số nullable trong JPQL). Tên field theo entity.
 */
public final class CareRecipientSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private CareRecipientSpecifications() {
    }

    // Hồ sơ chưa xóa mềm mà user đang quản lý, lọc theo trạng thái / tên.
    public static Specification<RecipientManager> managedBy(UUID userId, RecipientStatus status, String keyword) {
        return (root, query, criteriaBuilder) -> {
            Join<RecipientManager, CareRecipient> recipient = root.join("recipient");
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));
            predicates.add(criteriaBuilder.isNull(recipient.get("deletedAt")));
            if (status != null) {
                predicates.add(criteriaBuilder.equal(recipient.get("status"), status));
            }
            String pattern = toLikePattern(keyword);
            if (pattern != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(recipient.get("fullName")), pattern, LIKE_ESCAPE));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Admin: toàn hệ thống; mặc định ẩn hồ sơ đã lưu trữ (xóa mềm) trừ khi includeDeleted.
    public static Specification<CareRecipient> forAdmin(RecipientStatus status, String keyword, UUID createdBy,
                                                        boolean includeDeleted) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (createdBy != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
            }
            String pattern = toLikePattern(keyword);
            if (pattern != null) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("fullName")), pattern, LIKE_ESCAPE));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Escape ký tự đặc biệt của LIKE để keyword "50%" không thành wildcard.
    private static String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
