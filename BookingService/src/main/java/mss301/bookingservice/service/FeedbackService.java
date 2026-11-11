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
    // Danh sách đánh giá.
    APIResponse<PageResponse<FeedbackResponse>> list(UUID customerId, UUID caregiverId, UUID centerId, Integer rating, int page, int size);
    // Caregiver khách được đánh giá trong booking.
    APIResponse<List<EligibleCaregiverResponse>> eligible(UUID actorId, UUID bookingId);
    // Tạo đánh giá.
    APIResponse<FeedbackResponse> create(UUID actorId, FeedbackRequest request);
    // Trung tâm phản hồi đánh giá.
    APIResponse<FeedbackResponse> reply(UUID actorId, UUID centerId, UUID id, FeedbackReplyRequest request);
    // Ẩn / hiện đánh giá.
    APIResponse<FeedbackResponse> updateVisibility(UUID id, FeedbackVisibilityRequest request);
    // Tính điểm trung bình và số lượt đánh giá của caregiver.
    CaregiverRatingRequest summarizeRating(UUID caregiverId);
}
