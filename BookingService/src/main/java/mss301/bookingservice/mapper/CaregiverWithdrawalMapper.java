package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.CaregiverWithdrawalRequest;
import mss301.bookingservice.dto.response.CaregiverWithdrawalResponse;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CaregiverWithdrawal;
import mss301.bookingservice.enums.CaregiverWithdrawalState;
import mss301.bookingservice.enums.OccurrenceStatus;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Mapper(config = MapStructConfig.class)
public interface CaregiverWithdrawalMapper {

    // Một dòng cho mỗi buổi; booking / trung tâm / caregiver lấy từ chính buổi (đã khóa, đã kiểm thuộc caregiver).
    @IgnoreServerManagedFields
    @Mapping(target = "booking", source = "occurrence.booking")
    @Mapping(target = "occurrence", source = "occurrence")
    @Mapping(target = "centerId", source = "occurrence.booking.centerId")
    @Mapping(target = "caregiverId", source = "occurrence.caregiverId")
    @Mapping(target = "caregiverUserId", source = "occurrence.caregiverUserId")
    @Mapping(target = "reasonCode", source = "request.reasonCode")
    @Mapping(target = "reasonDetail", source = "reasonDetail")
    CaregiverWithdrawal toCaregiverWithdrawal(BookingOccurrence occurrence, CaregiverWithdrawalRequest request,
                                              String reasonDetail);

    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "recipientName", source = "booking.recipientSnapshot.fullName")
    @Mapping(target = "occurrenceId", source = "occurrence.id")
    @Mapping(target = "occurrenceNo", source = "occurrence.occurrenceNo")
    @Mapping(target = "startAt", source = "occurrence.startAt")
    @Mapping(target = "endAt", source = "occurrence.endAt")
    @Mapping(target = "occurrenceStatus", source = "occurrence.status")
    @Mapping(target = "currentCaregiverId", source = "occurrence.caregiverId")
    @Mapping(target = "state", expression = "java(resolveState(withdrawal, now))")
    CaregiverWithdrawalResponse toResponse(CaregiverWithdrawal withdrawal, @Context Instant now);

    List<CaregiverWithdrawalResponse> toResponseList(List<CaregiverWithdrawal> withdrawals, @Context Instant now);

    // Suy ra từ buổi (không lưu): đã sang người khác → REASSIGNED; chưa bắt đầu và còn của người xin hủy → PENDING.
    default CaregiverWithdrawalState resolveState(CaregiverWithdrawal withdrawal, Instant now) {
        BookingOccurrence occurrence = withdrawal.getOccurrence();
        if (!Objects.equals(occurrence.getCaregiverId(), withdrawal.getCaregiverId())) {
            return CaregiverWithdrawalState.REASSIGNED;
        }
        boolean pending = OccurrenceStatus.NOT_STARTED.contains(occurrence.getStatus())
                && occurrence.getStartAt().isAfter(now);
        return pending ? CaregiverWithdrawalState.PENDING : CaregiverWithdrawalState.CLOSED;
    }
}
