package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.ComplaintRequest;
import mss301.bookingservice.dto.response.ComplaintResponse;
import mss301.bookingservice.dto.response.ComplaintEventResponse;
import mss301.bookingservice.entity.Complaint;
import mss301.bookingservice.entity.ComplaintEvent;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.enums.ComplaintAction;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.client.dto.ComplaintRefundRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.util.UUID;
import java.util.List;
import java.math.BigDecimal;

@Mapper(config = MapStructConfig.class)
public interface ComplaintMapper {
    ComplaintResponse toResponse(Complaint complaint, List<ComplaintEventResponse> events);
    ComplaintEventResponse toEventResponse(ComplaintEvent event);
    List<ComplaintEventResponse> toEventResponseList(List<ComplaintEvent> events);

    @IgnoreServerManagedFields
    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "caregiverId", source = "caregiverId")
    @Mapping(target = "customerId", source = "booking.customerId")
    @Mapping(target = "centerId", source = "booking.centerId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "resolution", ignore = true)
    @Mapping(target = "resolvedBy", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "escalatedAt", ignore = true)
    @Mapping(target = "escalateReason", ignore = true)
    @Mapping(target = "refundAmount", ignore = true)
    @Mapping(target = "version", ignore = true)
    Complaint toComplaint(ComplaintRequest request, Booking booking, UUID caregiverId, String complaintCode);

    @IgnoreServerManagedFields
    ComplaintEvent toComplaintEvent(UUID complaintId, UUID actorId, ComplaintAction action,
                                   ComplaintStatus fromStatus, ComplaintStatus toStatus, String note);

    ComplaintRefundRequest toRefundRequest(UUID bookingId, UUID complaintId, BigDecimal amount, UUID decidedBy, String note);
}
