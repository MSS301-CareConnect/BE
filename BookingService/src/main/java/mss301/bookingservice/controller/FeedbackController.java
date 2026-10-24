package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.*;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.FeedbackResponse;
import mss301.bookingservice.dto.response.EligibleCaregiverResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.security.CurrentCenterId;
import mss301.bookingservice.service.FeedbackService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Feedback", description = "Đánh giá người chăm sóc và phản hồi trung tâm")
public class FeedbackController {
    FeedbackService feedbackService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<PageResponse<FeedbackResponse>> list(@RequestParam(required = false) UUID caregiverId,
            @RequestParam(required = false) UUID centerId, @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return feedbackService.list(null, caregiverId, centerId, rating, page, size);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<PageResponse<FeedbackResponse>> listMine(@CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return feedbackService.list(actorId, null, null, null, page, size);
    }

    @GetMapping("/eligible")
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<List<EligibleCaregiverResponse>> eligible(@CurrentUserId UUID actorId, @RequestParam UUID bookingId) {
        return feedbackService.eligible(actorId, bookingId);
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public APIResponse<FeedbackResponse> create(@CurrentUserId UUID actorId, @Valid @RequestBody FeedbackRequest request) {
        return feedbackService.create(actorId, request);
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('CENTER_MANAGER')")
    public APIResponse<FeedbackResponse> reply(@CurrentUserId UUID actorId, @CurrentCenterId UUID centerId,
                                               @PathVariable UUID id, @Valid @RequestBody FeedbackReplyRequest request) {
        return feedbackService.reply(actorId, centerId, id, request);
    }

    @PatchMapping("/{id}/visibility")
    @PreAuthorize("hasRole('ADMIN')")
    public APIResponse<FeedbackResponse> updateVisibility(@PathVariable UUID id,
            @Valid @RequestBody FeedbackVisibilityRequest request) {
        return feedbackService.updateVisibility(id, request);
    }
}
