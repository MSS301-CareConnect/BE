package mss301.bookingservice.mapper;

import mss301.bookingservice.client.dto.AvailabilityCheckRequest;
import mss301.bookingservice.dto.response.CaregiverOccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceResponse;
import mss301.bookingservice.dto.response.OccurrenceServiceResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.dto.response.SpecialNotesResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.OccurrenceStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface OccurrenceMapper {

    OccurrenceResponse toResponse(BookingOccurrence occurrence);

    List<OccurrenceResponse> toResponseList(List<BookingOccurrence> occurrences);

    // Buổi sinh cùng booking: caregiver mặc định theo booking; trạng thái ban đầu do service truyền vào.
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BookingOccurrence> toOccurrenceList(List<OccurrenceSlot> slots, @Context Booking booking,
                                             @Context OccurrenceStatus initialStatus);

    @IgnoreServerManagedFields
    @Mapping(target = "booking", expression = "java(booking)")
    @Mapping(target = "caregiverId", expression = "java(booking.getCaregiverId())")
    @Mapping(target = "caregiverUserId", expression = "java(booking.getCaregiverUserId())")
    @Mapping(target = "status", expression = "java(initialStatus)")
    // Giờ thực tế do caregiver ghi ở giai đoạn ca chăm sóc; slot Provider chưa nối (TODO) → để trống
    @Mapping(target = "acknowledgedAt", ignore = true)
    @Mapping(target = "actualStartAt", ignore = true)
    @Mapping(target = "actualEndAt", ignore = true)
    @Mapping(target = "availabilitySlotId", ignore = true)
    @Mapping(target = "cancelReason", ignore = true)
    @Mapping(target = "note", ignore = true)
    @Mapping(target = "version", ignore = true)
    // Kết quả vận hành buổi (OPS-06) — chỉ có sau khi buổi đến giờ.
    @Mapping(target = "missedReason", ignore = true)
    @Mapping(target = "noShowReportedAt", ignore = true)
    @Mapping(target = "noShowReportedBy", ignore = true)
    @Mapping(target = "noShowNote", ignore = true)
    @Mapping(target = "noShowDecidedBy", ignore = true)
    @Mapping(target = "noShowDecidedAt", ignore = true)
    @Mapping(target = "noShowRemindedAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "cancelledByParty", ignore = true)
    @Mapping(target = "needsReassignment", constant = "false")
    BookingOccurrence toOccurrence(OccurrenceSlot slot, @Context Booking booking, @Context OccurrenceStatus initialStatus);

    // Buổi đã lưu → khoảng thời gian để kiểm trùng lịch khi đổi caregiver.
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<OccurrenceSlot> toSlotList(List<BookingOccurrence> occurrences);

    // Danh sách lịch của caregiver: chưa kèm snapshot người bệnh (chỉ có ở chi tiết một buổi).
    @Named("toCaregiverResponse")
    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "bookingCode", source = "booking.bookingCode")
    @Mapping(target = "bookingStatus", source = "booking.status")
    @Mapping(target = "centerId", source = "booking.centerId")
    @Mapping(target = "recipientId", source = "booking.recipientId")
    @Mapping(target = "recipientName", source = "booking.recipientSnapshot.fullName")
    @Mapping(target = "serviceAddress", source = "booking.serviceAddress")
    @Mapping(target = "customerNote", source = "booking.customerNote")
    @Mapping(target = "recipientSnapshot", ignore = true)
    @Mapping(target = "services", ignore = true)
    @Mapping(target = "specialNotes", ignore = true)
    @Mapping(target = "customerPhone", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "reportStatus", ignore = true)
    CaregiverOccurrenceResponse toCaregiverResponse(BookingOccurrence occurrence);

    @IterableMapping(qualifiedByName = "toCaregiverResponse")
    List<CaregiverOccurrenceResponse> toCaregiverResponseList(List<BookingOccurrence> occurrences);

    // Chi tiết một buổi (caregiver được giao / service nội bộ): kèm snapshot người bệnh lúc đặt.
    @Named("toCaregiverDetailResponse")
    @InheritConfiguration(name = "toCaregiverResponse")
    @Mapping(target = "recipientSnapshot", source = "booking.recipientSnapshot")
    @Mapping(target = "specialNotes", source = "booking.recipientSnapshot")
    CaregiverOccurrenceResponse toCaregiverDetailResponse(BookingOccurrence occurrence);

    @Mapping(target = "medications", source = "activeMedications")
    SpecialNotesResponse toSpecialNotesResponse(RecipientSnapshot recipientSnapshot);

    OccurrenceServiceResponse toOccurrenceServiceResponse(BookingDetail detail);

    // C5.2: hỏi Provider caregiver có lịch làm việc / không nghỉ trong các buổi.
    AvailabilityCheckRequest toAvailabilityCheckRequest(UUID caregiverId, List<OccurrenceSlot> slots);

    AvailabilityCheckRequest.Slot toAvailabilitySlot(OccurrenceSlot slot);

    // Phần chi tiết cần dữ liệu ngoài buổi (dịch vụ của booking, SĐT khách từ Identity, session) — gắn sau khi map buổi.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "services", source = "services")
    @Mapping(target = "customerPhone", source = "customerPhone")
    @Mapping(target = "sessionId", source = "sessionId")
    void updateDetail(List<BookingDetail> services, String customerPhone, UUID sessionId,
                      @MappingTarget CaregiverOccurrenceResponse response);
}
