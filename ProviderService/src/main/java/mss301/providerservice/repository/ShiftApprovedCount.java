package mss301.providerservice.repository;

import java.util.UUID;

// Projection của query gom nhóm số đăng ký APPROVED theo ca (thay N+1 count khi liệt kê ca).
public interface ShiftApprovedCount {

    UUID getShiftId();

    long getApprovedCount();
}
