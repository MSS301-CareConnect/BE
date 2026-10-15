package mss301.bookingservice.repository;

import jakarta.persistence.criteria.Predicate;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.BookingStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
                                                BookingStatus status, LocalDate from, LocalDate to) {
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
                predicates.add(criteriaBuilder.equal(root.get("caregiverId"), caregiverId));
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
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
