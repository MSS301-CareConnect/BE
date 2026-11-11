package mss301.bookingservice.util;

import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.enums.RoleCode;
import java.util.Set;
import java.util.UUID;

public final class AftercareScope {
    // Lớp tiện ích tĩnh, không cho khởi tạo.
    private AftercareScope() {
    }

    // Người gọi được xem ca chăm sóc (admin, trung tâm, khách, caregiver).
    public static boolean canReadSession(UUID actorId, Set<RoleCode> roles, UUID centerId, CareSession session) {
        return roles.contains(RoleCode.ADMIN)
                || (roles.contains(RoleCode.CENTER_MANAGER) && session.getBooking().getCenterId().equals(centerId))
                || (roles.contains(RoleCode.CUSTOMER) && session.getBooking().getCustomerId().equals(actorId))
                || (roles.contains(RoleCode.CAREGIVER) && session.getCaregiverUserId().equals(actorId));
    }
}
