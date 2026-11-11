package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OperationalStatisticsResponse;
import mss301.bookingservice.enums.RoleCode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface StatisticsService {
    // Thống kê vận hành của trung tâm.
    APIResponse<OperationalStatisticsResponse> centers(Set<RoleCode> roles, UUID scopeCenterId, UUID centerId, LocalDate from, LocalDate to);
    // Thống kê vận hành toàn hệ thống.
    APIResponse<OperationalStatisticsResponse> system(LocalDate from, LocalDate to, int page, int size);
}
