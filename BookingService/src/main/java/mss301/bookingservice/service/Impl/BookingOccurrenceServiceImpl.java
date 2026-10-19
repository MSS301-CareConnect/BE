package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.service.BookingOccurrenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookingOccurrenceServiceImpl implements BookingOccurrenceService {

    BookingOccurrenceRepository bookingOccurrenceRepository;
    OccurrenceMapper occurrenceMapper;
    BookingProperties bookingProperties;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<CaregiverOccurrenceResponse>> list(UUID actorId, LocalDate from, LocalDate to,
                                                               OccurrenceStatus status) {
        if (to.isBefore(from)
                || ChronoUnit.DAYS.between(from, to) + 1 > bookingProperties.getSchedule().getMaxCaregiverRangeDays()) {
            throw new AppException(ErrorCode.INVALID_DATE_FILTER);
        }
        ZoneId zone = bookingProperties.getSchedule().getTimezone();
        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        Set<OccurrenceStatus> statuses = status == null ? EnumSet.allOf(OccurrenceStatus.class) : EnumSet.of(status);
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findCaregiverSchedule(actorId, rangeStart,
                rangeEnd, statuses, BookingStatus.CAREGIVER_VISIBLE);
        List<CaregiverOccurrenceResponse> occurrenceResponses = occurrenceMapper.toCaregiverResponseList(occurrences);
        return APIResponse.success("Lấy lịch chăm sóc thành công", occurrenceResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverOccurrenceResponse> get(UUID actorId, UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findForCaregiver(id, actorId,
                        BookingStatus.CAREGIVER_VISIBLE)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        return APIResponse.success("Lấy chi tiết buổi chăm sóc thành công", occurrenceResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverOccurrenceResponse> getById(UUID id) {
        BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(id)
                .orElseThrow(() -> new AppException(ErrorCode.OCCURRENCE_NOT_FOUND));
        CaregiverOccurrenceResponse occurrenceResponse = occurrenceMapper.toCaregiverDetailResponse(occurrence);
        return APIResponse.success("Lấy buổi chăm sóc thành công", occurrenceResponse);
    }
}
