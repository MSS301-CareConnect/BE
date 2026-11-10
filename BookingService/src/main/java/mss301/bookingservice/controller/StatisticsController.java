package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OperationalStatisticsResponse;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.security.CurrentRoles;
import mss301.bookingservice.service.StatisticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Statistics", description = "Thống kê vận hành, không bao gồm doanh thu")
public class StatisticsController {
    StatisticsService statisticsService;

    @GetMapping("/centers")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<OperationalStatisticsResponse> centers(@CurrentRoles Set<RoleCode> roles,
            @CurrentCenterId(required = false) UUID scopeCenterId, @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return statisticsService.centers(roles, scopeCenterId, centerId, from, to);
    }

    @GetMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<OperationalStatisticsResponse> system(@RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return statisticsService.system(from, to, page, size);
    }
}
