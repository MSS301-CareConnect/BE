package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.FeedbackRequest;
import mss301.bookingservice.dto.request.FeedbackReplyRequest;
import mss301.bookingservice.dto.request.FeedbackVisibilityRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.FeedbackResponse;
import mss301.bookingservice.dto.response.EligibleCaregiverResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.client.dto.CaregiverRatingRequest;
import java.util.UUID;
import java.util.List;

public interface FeedbackService {
    APIResponse<PageResponse<FeedbackResponse>> list(UUID customerId, UUID caregiverId, UUID centerId, Integer rating, int page, int size);
    APIResponse<List<EligibleCaregiverResponse>> eligible(UUID actorId, UUID bookingId);
    APIResponse<FeedbackResponse> create(UUID actorId, FeedbackRequest request);
    APIResponse<FeedbackResponse> reply(UUID actorId, UUID centerId, UUID id, FeedbackReplyRequest request);
    APIResponse<FeedbackResponse> updateVisibility(UUID id, FeedbackVisibilityRequest request);
    CaregiverRatingRequest summarizeRating(UUID caregiverId);
}
