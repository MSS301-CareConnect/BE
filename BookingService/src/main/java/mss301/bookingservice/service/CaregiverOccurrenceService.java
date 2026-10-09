package mss301.bookingservice.service;

import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.enums.OccurrenceStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lịch được giao của caregiver (actorId = sub = caregiver_user_id). Chỉ thấy buổi của mình thuộc booking đã xác nhận;
 * buổi khác → 404. TODO(giai đoạn ca chăm sóc): bắt đầu / kết thúc ca, checklist, care report.
 */
public interface CaregiverOccurrenceService {
    APIResponse<List<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to, OccurrenceStatus status);

    APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, UUID id);
}
