package mss301.providerservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.dto.request.RegistrationBulkReviewRequest;
import mss301.providerservice.dto.request.RegistrationReviewRequest;
import mss301.providerservice.dto.request.ShiftRegistrationRequest;
import mss301.providerservice.dto.request.WorkShiftBatchRequest;
import mss301.providerservice.dto.request.WorkShiftCancelRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.request.WorkShiftStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.dto.response.WorkShiftStatisticsResponse;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.security.CurrentUserId;
import mss301.providerservice.service.ShiftRegistrationService;
import mss301.providerservice.service.WorkShiftService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-shifts")
@Tag(name = "Work Shifts", description = "Quản lý ca làm việc và đăng ký ca")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkShiftController {

    WorkShiftService workShiftService;
    ShiftRegistrationService shiftRegistrationService;

    // ─── GET OPERATIONS ───────────────────────────────────────────────────────

    // API danh sách ca làm việc có lọc và phân trang.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<PageResponse<WorkShiftResponse>> list(
            @CurrentUserId UUID actorId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) ShiftStatus status,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String shiftType,
            @RequestParam(defaultValue = "false") boolean hasVacancy,
            @RequestParam(defaultValue = "false") boolean hasPending,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // status lọc theo trạng thái HIỆU LỰC: ca đã qua giờ bắt đầu / hạn đăng ký không còn nằm trong OPEN.
        return workShiftService.list(actorId, centerId, status, fromDate, toDate, shiftType, hasVacancy, hasPending,
                page, size);
    }

    // API thống kê ca làm việc của trung tâm.
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftStatisticsResponse> statistics(
            @CurrentUserId UUID actorId,
            @RequestParam(required = false) UUID centerId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(required = false) String shiftType) {
        return workShiftService.statistics(actorId, centerId, fromDate, toDate, shiftType);
    }

    // API xem chi tiết ca làm việc.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CAREGIVER')")
    public APIResponse<WorkShiftResponse> get(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return workShiftService.get(actorId, id);
    }

    // API danh sách đăng ký ca của caregiver đang đăng nhập.
    @GetMapping("/registrations/me")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listMyRegistrations(
            @CurrentUserId UUID actorId,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftRegistrationService.listMine(actorId, status, page, size);
    }

    // API danh sách đăng ký của một ca.
    @GetMapping("/{id}/registrations")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<PageResponse<ShiftRegistrationResponse>> listRegistrations(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam(required = false) RegistrationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return shiftRegistrationService.listByShift(actorId, id, status, page, size);
    }

    // ─── POST OPERATIONS ──────────────────────────────────────────────────────

    // API manager tạo ca làm việc.
    @PostMapping("/centers/{centerId}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> create(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID centerId,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return workShiftService.create(actorId, centerId, request);
    }

    // Tạo lặp lại: cùng giờ / loại / sức chứa cho 1–31 ngày, tất cả hoặc không ca nào.
    @PostMapping("/centers/{centerId}/batch")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<List<WorkShiftResponse>> createBatch(@CurrentUserId UUID actorId,
                                                            @PathVariable UUID centerId,
                                                            @Valid @RequestBody WorkShiftBatchRequest request) {
        return workShiftService.createBatch(actorId, centerId, request);
    }

    // Hủy ca trước giờ bắt đầu (bắt buộc lý do), kể cả khi đã có người được duyệt nhưng chưa có buổi chăm sóc.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> cancel(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftCancelRequest request) {
        return workShiftService.cancel(actorId, id, request);
    }

    // API caregiver đăng ký nhận ca.
    @PostMapping("/{shiftId}/registrations")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<ShiftRegistrationResponse> register(
            @CurrentUserId UUID actorId,
            @PathVariable UUID shiftId,
            @Valid @RequestBody ShiftRegistrationRequest request) {
        return shiftRegistrationService.register(actorId, shiftId, request);
    }

    // ─── PUT OPERATIONS ───────────────────────────────────────────────────────

    // API cập nhật ca làm việc.
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> update(@CurrentUserId UUID actorId,
                                                 @PathVariable UUID id,
                                                 @Valid @RequestBody WorkShiftRequest request) {
        return workShiftService.update(actorId, id, request);
    }

    // ─── PATCH OPERATIONS ─────────────────────────────────────────────────────

    // API manager duyệt / từ chối một đăng ký ca.
    @PatchMapping("/registrations/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<ShiftRegistrationResponse> reviewRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationReviewRequest request) {
        return shiftRegistrationService.review(actorId, id, request);
    }

    // Mở / đóng đăng ký thủ công (OPEN | CLOSED).
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<WorkShiftResponse> changeStatus(@CurrentUserId UUID actorId,
                                                       @PathVariable UUID id,
                                                       @Valid @RequestBody WorkShiftStatusRequest request) {
        return workShiftService.changeStatus(actorId, id, request);
    }

    // API manager duyệt / từ chối nhiều đăng ký ca một lần.
    @PatchMapping("/{id}/registrations/review")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<List<ShiftRegistrationResponse>> bulkReviewRegistrations(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @Valid @RequestBody RegistrationBulkReviewRequest request) {
        return shiftRegistrationService.bulkReview(actorId, id, request);
    }

    // ─── DELETE OPERATIONS ────────────────────────────────────────────────────

    // API hủy / xóa ca làm việc.
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId,
                                    @PathVariable UUID id,
                                    @RequestParam Long version) {
        return workShiftService.delete(actorId, id, version);
    }

    // API caregiver hủy đăng ký ca.
    @DeleteMapping("/registrations/{id}")
    @PreAuthorize("hasRole('CAREGIVER')")
    public APIResponse<Void> cancelRegistration(
            @CurrentUserId UUID actorId,
            @PathVariable UUID id,
            @RequestParam Long version) {
        return shiftRegistrationService.cancel(actorId, id, version);
    }
}
