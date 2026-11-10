package mss301.bookingservice.util;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filter động cho danh sách booking (JPA Specification — tránh tham số nullable trong JPQL). Tham số null = bỏ qua.
 * Phạm vi (customer / trung tâm) do service truyền vào từ token, không từ client.
 */
public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    // from/to: booking có khoảng ngày [start_date, end_date] giao với [from, to].
    public static Specification<Booking> filter(UUID customerId, UUID centerId, UUID recipientId, UUID caregiverId,
                                                BookingStatus status, LocalDate from, LocalDate to, String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            if (customerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("customerId"), customerId));
            }
            if (centerId != null) {
                predicates.add(criteriaBuilder.equal(root.get("centerId"), centerId));
            }
            if (recipientId != null) {
                predicates.add(criteriaBuilder.equal(root.get("recipientId"), recipientId));
            }
            if (caregiverId != null) {
                // Caregiver cấp booking hoặc được giao riêng ít nhất một buổi (BK-05: đổi caregiver từng buổi).
                Subquery<UUID> assignedOccurrence = query.subquery(UUID.class);
                Root<BookingOccurrence> occurrence = assignedOccurrence.from(BookingOccurrence.class);
                assignedOccurrence.select(occurrence.get("id"))
                        .where(criteriaBuilder.equal(occurrence.get("booking"), root),
                                criteriaBuilder.equal(occurrence.get("caregiverId"), caregiverId),
                                criteriaBuilder.isNull(occurrence.get("deletedAt")));
                predicates.add(criteriaBuilder.or(criteriaBuilder.equal(root.get("caregiverId"), caregiverId),
                        criteriaBuilder.exists(assignedOccurrence)));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startDate"), to));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + escapeLike(keyword.strip().toUpperCase(Locale.ROOT)) + "%";
                predicates.add(criteriaBuilder.like(criteriaBuilder.upper(root.get("bookingCode")), pattern, '\\'));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Lọc nhiều trạng thái (null / rỗng = bỏ qua).
    public static Specification<Booking> statusIn(Collection<BookingStatus> statuses) {
        return (root, query, criteriaBuilder) -> statuses == null || statuses.isEmpty()
                ? null
                : root.get("status").in(statuses);
    }

    // Sắp theo hạn đang chạy (cùng quy tắc util/BookingDeadlines); booking không có hạn xếp cuối. Truy vấn đếm của
    // Spring Data bỏ orderBy nên chỉ ảnh hưởng truy vấn lấy trang.
    public static Specification<Booking> orderByDeadline(Sort.Direction direction) {
        return (root, query, criteriaBuilder) -> {
            Path<BookingStatus> status = root.get("status");
            Expression<Instant> deadline = criteriaBuilder.<Instant>selectCase()
                    .when(status.in(BookingStatus.PENDING_CENTER_CONFIRMATION, BookingStatus.PENDING_CAREGIVER_CONFIRMATION),
                            root.<Instant>get("acceptanceDueAt"))
                    .when(criteriaBuilder.equal(status, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT),
                            root.<Instant>get("assignmentDueAt"))
                    .when(criteriaBuilder.equal(status, BookingStatus.AWAITING_INITIAL_PAYMENT),
                            root.<Instant>get("depositDueAt"))
                    .when(criteriaBuilder.and(criteriaBuilder.equal(status, BookingStatus.CONFIRMED),
                            criteriaBuilder.isNull(root.get("fullyPaidAt"))), root.<Instant>get("remainingPaymentDueAt"))
                    .otherwise(criteriaBuilder.nullLiteral(Instant.class));
            Expression<Integer> missing = criteriaBuilder.<Integer>selectCase()
                    .when(criteriaBuilder.isNull(deadline), 1)
                    .otherwise(0);
            query.orderBy(criteriaBuilder.asc(missing),
                    direction == Sort.Direction.DESC ? criteriaBuilder.desc(deadline) : criteriaBuilder.asc(deadline),
                    criteriaBuilder.asc(root.get("id")));
            return null;
        };
    }

    // Ký tự đại diện của LIKE trong từ khóa được hiểu là chữ thường, không phải wildcard.
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
