package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.FeedbackRequest;
import mss301.bookingservice.dto.request.FeedbackReplyRequest;
import mss301.bookingservice.dto.request.FeedbackVisibilityRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.FeedbackResponse;
import mss301.bookingservice.dto.response.EligibleCaregiverResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Feedback;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.FeedbackVisibility;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.FeedbackMapper;
import mss301.bookingservice.repository.FeedbackRepository;
import mss301.bookingservice.repository.FeedbackRatingView;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.service.FeedbackService;
import mss301.bookingservice.service.IdentityUserService;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.service.BookingOutboxService;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.CaregiverRatingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {
    FeedbackRepository feedbackRepository;
    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    FeedbackMapper feedbackMapper;
    IdentityUserService identityUserService;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    BookingOutboxService bookingOutboxService;
    TransactionTemplate transactionTemplate;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<FeedbackResponse>> list(UUID customerId, UUID caregiverId, UUID centerId, Integer rating, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new AppException(ErrorCode.RATING_OUT_OF_RANGE);
        }
        Page<Feedback> feedbacks = feedbackRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (customerId == null) {
                predicates.add(builder.equal(root.get("visibility"), FeedbackVisibility.VISIBLE));
            } else {
                predicates.add(builder.equal(root.get("customerId"), customerId));
            }
            if (caregiverId != null) {
                predicates.add(builder.equal(root.get("caregiverId"), caregiverId));
            }
            if (centerId != null) {
                predicates.add(builder.equal(root.get("centerId"), centerId));
            }
            if (rating != null) {
                predicates.add(builder.equal(root.get("rating"), rating));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PageResponse<FeedbackResponse> response = PageResponse.of(feedbacks, feedbackMapper.toResponseList(feedbacks.getContent()));
        return APIResponse.success("Lấy đánh giá thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<EligibleCaregiverResponse>> eligible(UUID actorId, UUID bookingId) {
        Booking booking = findOwnedBooking(actorId, bookingId);
        checkBookingStatus(booking);
        Map<UUID, EligibleCaregiverResponse> eligible = new LinkedHashMap<>();
        for (BookingOccurrence occurrence : bookingOccurrenceRepository.findAllByBooking(bookingId)) {
            if (occurrence.getStatus() == OccurrenceStatus.COMPLETED
                    && !feedbackRepository.existsByBookingIdAndCaregiverIdAndDeletedAtIsNull(bookingId, occurrence.getCaregiverId())) {
                eligible.putIfAbsent(occurrence.getCaregiverId(), feedbackMapper.toEligibleResponse(occurrence));
            }
        }
        List<EligibleCaregiverResponse> response = new ArrayList<>(eligible.values());
        return APIResponse.success("Lấy người chăm sóc có thể đánh giá thành công", response);
    }

    @Override
    public APIResponse<FeedbackResponse> create(UUID actorId, FeedbackRequest request) {
        IdentityUserResponse profile = identityUserService.getProfiles(List.of(actorId)).get(actorId);
        String displayName = abbreviateName(profile == null ? null : profile.getFullName());
        FeedbackResponse response = transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findForUpdate(request.getBookingId())
                    .filter(found -> actorId.equals(found.getCustomerId()))
                    .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
            checkBookingStatus(booking);
            if (feedbackRepository.existsByBookingIdAndCaregiverIdAndDeletedAtIsNull(booking.getId(), request.getCaregiverId())) {
                throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
            }
            BookingOccurrence occurrence = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                    .filter(found -> request.getCaregiverId().equals(found.getCaregiverId()) && found.getStatus() == OccurrenceStatus.COMPLETED)
                    .findFirst().orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_ALLOWED));
            Feedback feedback = feedbackMapper.toFeedback(request, booking, occurrence, displayName);
            feedback.setVisibility(FeedbackVisibility.VISIBLE);
            Feedback saved = feedbackRepository.saveAndFlush(feedback);
            enqueueRating(saved);
            bookingNotificationService.notifyCenter(booking.getCenterId(), NotificationType.REVIEW_CREATED, "FEEDBACK", saved.getId());
            return feedbackMapper.toResponse(saved);
        });
        return APIResponse.success("Gửi đánh giá thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<FeedbackResponse> reply(UUID actorId, UUID centerId, UUID id, FeedbackReplyRequest request) {
        Feedback feedback = feedbackRepository.findForUpdate(id)
                .filter(found -> found.getCenterId().equals(centerId))
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));
        if (feedback.getReply() != null) {
            throw new AppException(ErrorCode.REPLY_ALREADY_EXISTS);
        }
        feedback.setReply(request.getReply().trim());
        feedback.setRepliedBy(actorId);
        feedback.setRepliedAt(clock.instant());
        notificationService.notify(List.of(feedback.getCustomerId(), feedback.getCaregiverUserId()), NotificationType.REVIEW_REPLIED,
                Map.of(), "FEEDBACK", id);
        FeedbackResponse response = feedbackMapper.toResponse(feedbackRepository.saveAndFlush(feedback));
        return APIResponse.success("Phản hồi đánh giá thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<FeedbackResponse> updateVisibility(UUID id, FeedbackVisibilityRequest request) {
        Feedback feedback = feedbackRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.REVIEW_NOT_FOUND));
        if (!request.getVisible() && (request.getReason() == null || request.getReason().isBlank())) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        FeedbackVisibility visibility = request.getVisible() ? FeedbackVisibility.VISIBLE : FeedbackVisibility.HIDDEN;
        if (feedback.getVisibility() != visibility) {
            feedback.setVisibility(visibility);
            feedback.setHiddenReason(request.getVisible() ? null : request.getReason().trim());
            feedbackRepository.flush();
            enqueueRating(feedback);
        }
        FeedbackResponse response = feedbackMapper.toResponse(feedback);
        return APIResponse.success("Cập nhật hiển thị đánh giá thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public CaregiverRatingRequest summarizeRating(UUID caregiverId) {
        FeedbackRatingView summary = feedbackRepository.summarizeRating(caregiverId, FeedbackVisibility.VISIBLE);
        BigDecimal average = summary.getRatingAverage() == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(summary.getRatingAverage()).setScale(2, RoundingMode.HALF_UP);
        return feedbackMapper.toRatingRequest(average, Math.toIntExact(summary.getRatingCount()));
    }

    private void enqueueRating(Feedback feedback) {
        // Mỗi retry tính lại từ dữ liệu mới nhất, không gửi snapshot rating cũ.
        bookingOutboxService.enqueue(OutboxEventType.RATING, feedback.getCaregiverId(),
                "RATING:" + feedback.getId() + ":" + UUID.randomUUID(), feedback.getCaregiverId());
    }

    private Booking findOwnedBooking(UUID actorId, UUID id) {
        return bookingRepository.findByIdAndDeletedAtIsNull(id).filter(found -> actorId.equals(found.getCustomerId()))
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private void checkBookingStatus(Booking booking) {
        if (booking.getStatus() != BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.CANCELLED) {
            throw new AppException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
    }

    private String abbreviateName(String name) {
        if (name == null || name.isBlank()) {
            return "Khách hàng";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            result.append(' ').appendCodePoint(parts[i].codePointAt(0)).append('.');
        }
        return result.toString();
    }
}
