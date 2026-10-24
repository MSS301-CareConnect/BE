package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.FeedbackRequest;
import mss301.bookingservice.dto.response.FeedbackResponse;
import mss301.bookingservice.dto.response.EligibleCaregiverResponse;
import mss301.bookingservice.entity.Feedback;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.client.dto.CaregiverRatingRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.math.BigDecimal;
import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface FeedbackMapper {
    @Mapping(target = "bookingId", source = "booking.id")
    FeedbackResponse toResponse(Feedback feedback);
    List<FeedbackResponse> toResponseList(List<Feedback> feedbacks);
    EligibleCaregiverResponse toEligibleResponse(BookingOccurrence occurrence);
    CaregiverRatingRequest toRatingRequest(BigDecimal ratingAverage, Integer ratingCount);

    @IgnoreServerManagedFields
    @Mapping(target = "booking", source = "booking")
    @Mapping(target = "caregiverId", source = "occurrence.caregiverId")
    @Mapping(target = "caregiverUserId", source = "occurrence.caregiverUserId")
    @Mapping(target = "customerId", source = "booking.customerId")
    @Mapping(target = "centerId", source = "booking.centerId")
    @Mapping(target = "reply", ignore = true)
    @Mapping(target = "repliedBy", ignore = true)
    @Mapping(target = "repliedAt", ignore = true)
    @Mapping(target = "visibility", ignore = true)
    @Mapping(target = "hiddenReason", ignore = true)
    Feedback toFeedback(FeedbackRequest request, Booking booking, BookingOccurrence occurrence, String customerDisplayName);
}
