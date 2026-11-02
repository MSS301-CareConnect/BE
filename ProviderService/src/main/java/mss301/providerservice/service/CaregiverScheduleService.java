package mss301.providerservice.service;

import mss301.providerservice.dto.request.ScheduleStatusRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.dto.response.BookableScheduleResponse;
import mss301.providerservice.dto.response.ScheduleResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CaregiverScheduleService {

    APIResponse<PageResponse<ScheduleResponse>> listMine(UUID actorId, int page, int size);

    APIResponse<PageResponse<ScheduleResponse>> listByCenter(UUID actorId, UUID centerId, int page, int size);

    APIResponse<ScheduleResponse> updateStatus(UUID actorId, UUID id, ScheduleStatusRequest request);

    // BookingService (token SERVICE) xác minh lịch khách chọn cho booking SHIFT.
    APIResponse<ScheduleResponse> get(UUID id);

    // Khách xem lịch SCHEDULED sắp tới của một caregiver đặt được (spec booking §3).
    APIResponse<List<BookableScheduleResponse>> listBookable(UUID caregiverId, LocalDate from, LocalDate to);
}
