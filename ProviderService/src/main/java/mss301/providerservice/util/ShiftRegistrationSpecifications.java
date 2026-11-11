package mss301.providerservice.util;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.enums.RegistrationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ShiftRegistrationSpecifications {

    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private ShiftRegistrationSpecifications() {
    }

    // Điều kiện lọc đăng ký chưa xóa của một ca, theo trạng thái nếu có.
    public static Specification<ShiftRegistration> ofShift(UUID shiftId, RegistrationStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            predicates.add(criteriaBuilder.equal(root.get("workShift").get("id"), shiftId));
            addStatus(predicates, criteriaBuilder, root.get("registrationStatus"), status);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Điều kiện lọc đăng ký chưa xóa của một caregiver, theo trạng thái nếu có.
    public static Specification<ShiftRegistration> ofCaregiver(UUID caregiverId, RegistrationStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));
            predicates.add(criteriaBuilder.equal(root.get("caregiver").get("id"), caregiverId));
            addStatus(predicates, criteriaBuilder, root.get("registrationStatus"), status);
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    // Thêm điều kiện trạng thái khi có giá trị.
    private static void addStatus(List<Predicate> predicates, CriteriaBuilder criteriaBuilder,
                                  Path<Object> path, RegistrationStatus status) {
        if (status != null) {
            predicates.add(criteriaBuilder.equal(path, status));
        }
    }
}
