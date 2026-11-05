package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.CaregiverChangeApproveRequest;
import mss301.bookingservice.dto.request.CaregiverChangeCreateRequest;
import mss301.bookingservice.dto.request.CaregiverChangeRejectRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.CaregiverChangeRequestResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingEvent;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.entity.CaregiverChangeRequest;
import mss301.bookingservice.entity.CaregiverChangeRequestOccurrence;
import mss301.bookingservice.entity.CaregiverChangeRequestOccurrenceId;
import mss301.bookingservice.enums.BookingEventType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CaregiverChangeRequestStatus;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.OccurrenceStatus;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.CaregiverChangeRequestMapper;
import mss301.bookingservice.mapper.OccurrenceMapper;
import mss301.bookingservice.repository.BookingEventRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.CaregiverChangeRequestRepository;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.CaregiverChangeRequestService;
import mss301.bookingservice.service.CaregiverPort;
import mss301.bookingservice.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverChangeRequestServiceImpl implements CaregiverChangeRequestService {

    CaregiverChangeRequestRepository changeRequestRepository;
    BookingRepository bookingRepository;
    BookingOccurrenceRepository occurrenceRepository;
    BookingEventRepository eventRepository;
    CaregiverChangeRequestMapper mapper;
    OccurrenceMapper occurrenceMapper;
    CaregiverPort caregiverPort;
    BookingNotificationService bookingNotificationService;
    NotificationService notificationService;
    TransactionTemplate transactionTemplate;
    Clock clock;

    static final int MAX_REQUESTS_PER_BOOKING = 3;
    static final Duration MIN_NOTICE_DURATION = Duration.ofHours(12);

    @Override
    public APIResponse<CaregiverChangeRequestResponse> createRequest(UUID actorId, UUID bookingId, CaregiverChangeCreateRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        if (!Objects.equals(booking.getCustomerId(), actorId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }

        if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_BOOKING_TRANSITION);
        }

        long pendingCount = changeRequestRepository.countByBookingIdAndStatus(bookingId, CaregiverChangeRequestStatus.PENDING);
        if (pendingCount > 0) {
            throw new AppException(ErrorCode.CONCURRENT_UPDATE);
        }

        long totalCount = changeRequestRepository.countByBookingId(bookingId);
        if (totalCount >= MAX_REQUESTS_PER_BOOKING) {
            throw new AppException(ErrorCode.TOO_MANY_CHANGE_REQUESTS);
        }

        List<BookingOccurrence> activeOccurrences = occurrenceRepository.findAllByBooking(bookingId).stream()
                .filter(o -> o.getStatus() == OccurrenceStatus.SCHEDULED || o.getStatus() == OccurrenceStatus.ACKNOWLEDGED)
                .toList();

        List<BookingOccurrence> targetOccurrences = new ArrayList<>();
        Instant now = clock.instant();

        switch (request.getScopeType()) {
            case ALL_REMAINING -> targetOccurrences.addAll(activeOccurrences);
            case SPECIFIC_OCCURRENCES -> {
                if (request.getOccurrenceIds() == null || request.getOccurrenceIds().isEmpty()) {
                    throw new AppException(ErrorCode.OCCURRENCE_ID_REQUIRED);
                }
                Set<UUID> requestedIds = Set.copyOf(request.getOccurrenceIds());
                targetOccurrences.addAll(activeOccurrences.stream()
                        .filter(o -> requestedIds.contains(o.getId()))
                        .toList());
            }
            case FROM_DATE -> {
                if (request.getFromDate() == null) {
                    throw new AppException(ErrorCode.START_DATE_REQUIRED);
                }
                Instant fromInstant = request.getFromDate().atStartOfDay(ZoneId.of("UTC")).toInstant();
                targetOccurrences.addAll(activeOccurrences.stream()
                        .filter(o -> !o.getStartAt().isBefore(fromInstant))
                        .toList());
            }
        }

        if (targetOccurrences.isEmpty()) {
            throw new AppException(ErrorCode.NO_REASSIGNABLE_OCCURRENCES);
        }

        Instant earliest = targetOccurrences.stream()
                .map(BookingOccurrence::getStartAt)
                .min(Comparator.naturalOrder())
                .orElse(now);

        if (earliest.isBefore(now.plus(MIN_NOTICE_DURATION))) {
            throw new AppException(ErrorCode.CHANGE_REQUEST_TOO_LATE);
        }

        CaregiverChangeRequest saved = transactionTemplate.execute(status -> {
            CaregiverChangeRequest changeRequest = CaregiverChangeRequest.builder()
                    .booking(booking)
                    .customerUserId(actorId)
                    .centerId(booking.getCenterId())
                    .scopeType(request.getScopeType())
                    .fromDate(request.getFromDate())
                    .reasonCode(request.getReasonCode())
                    .reasonDetail(request.getReasonDetail())
                    .preferredCaregiverId(request.getPreferredCaregiverId())
                    .status(CaregiverChangeRequestStatus.PENDING)
                    .build();

            for (BookingOccurrence occ : targetOccurrences) {
                changeRequest.getOccurrences().add(CaregiverChangeRequestOccurrence.builder()
                        .id(new CaregiverChangeRequestOccurrenceId(null, occ.getId()))
                        .request(changeRequest)
                        .occurrence(occ)
                        .build());
            }

            CaregiverChangeRequest entity = changeRequestRepository.save(changeRequest);

            BookingEvent event = BookingEvent.builder()
                    .booking(booking)
                    .eventType(BookingEventType.CAREGIVER_CHANGE_REQUESTED)
                    .changedBy(actorId)
                    .reason(request.getReasonCode().name() + (request.getReasonDetail() != null ? ": " + request.getReasonDetail() : ""))
                    .build();
            eventRepository.save(event);

            return entity;
        });

        bookingNotificationService.notifyCenter(
                booking.getCenterId(),
                NotificationType.CAREGIVER_CHANGE_REQUEST_CREATED,
                Map.of("body", "Khách hàng gửi yêu cầu đổi người chăm sóc cho lịch " + booking.getBookingCode()),
                "CAREGIVER_CHANGE_REQUEST",
                saved.getId()
        );

        log.info("Caregiver change request {} created for booking {} by customer {}", saved.getId(), bookingId, actorId);
        return APIResponse.success("Gửi yêu cầu đổi người chăm sóc thành công", mapper.toResponse(saved));
    }

    @Override
    public APIResponse<List<CaregiverChangeRequestResponse>> listRequestsByBooking(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));

        boolean isCustomer = roles.contains(RoleCode.CUSTOMER) && Objects.equals(booking.getCustomerId(), actorId);
        boolean isCenterManager = roles.contains(RoleCode.CENTER_MANAGER) && Objects.equals(booking.getCenterId(), centerId);
        boolean isAdmin = roles.contains(RoleCode.ADMIN);

        if (!isCustomer && !isCenterManager && !isAdmin) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }

        List<CaregiverChangeRequest> requests = changeRequestRepository.findByBookingIdOrderByCreatedAtDesc(bookingId);
        return APIResponse.success("Lấy danh sách yêu cầu đổi người thành công", mapper.toResponseList(requests));
    }

    @Override
    public APIResponse<PageResponse<CaregiverChangeRequestResponse>> listCenterRequests(UUID actorId, UUID centerId, CaregiverChangeRequestStatus status, int page, int size) {
        Page<CaregiverChangeRequest> pageResult = changeRequestRepository.findByCenterIdAndStatus(
                centerId, status, PageRequest.of(page, size)
        );
        return APIResponse.success("Lấy danh sách yêu cầu thành công", PageResponse.of(pageResult, mapper.toResponseList(pageResult.getContent())));
    }

    @Override
    public APIResponse<CaregiverChangeRequestResponse> approveRequest(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeApproveRequest request) {
        CaregiverChangeRequest changeRequest = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND));

        if (!Objects.equals(changeRequest.getCenterId(), centerId)) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND);
        }

        if (changeRequest.getStatus() != CaregiverChangeRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_PENDING);
        }

        Booking booking = changeRequest.getBooking();

        List<BookingOccurrence> targetOccurrences = changeRequest.getOccurrences().stream()
                .map(CaregiverChangeRequestOccurrence::getOccurrence)
                .filter(o -> o.getStatus() == OccurrenceStatus.SCHEDULED || o.getStatus() == OccurrenceStatus.ACKNOWLEDGED)
                .toList();

        if (targetOccurrences.isEmpty()) {
            throw new AppException(ErrorCode.NO_REASSIGNABLE_OCCURRENCES);
        }

        List<UUID> careServiceIds = targetOccurrences.stream()
                .map(BookingOccurrence::getCareServiceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        AssignedCaregiver newCaregiver = caregiverPort.resolveAssignable(centerId, request.getNewCaregiverId(), careServiceIds);

        List<OccurrenceSlot> slots = targetOccurrences.stream().map(occurrenceMapper::toSlot).toList();
        caregiverPort.checkAvailable(newCaregiver.getCaregiverId(), slots);

        Instant now = clock.instant();

        CaregiverChangeRequest updated = transactionTemplate.execute(status -> {
            for (BookingOccurrence occurrence : targetOccurrences) {
                UUID oldCaregiverId = occurrence.getCaregiverId();
                occurrence.assignCaregiver(newCaregiver.getCaregiverId(), newCaregiver.getCaregiverUserId());
                occurrenceRepository.save(occurrence);

                BookingEvent event = BookingEvent.builder()
                        .booking(booking)
                        .occurrenceId(occurrence.getId())
                        .eventType(BookingEventType.CAREGIVER_CHANGED)
                        .oldCaregiverId(oldCaregiverId)
                        .newCaregiverId(newCaregiver.getCaregiverId())
                        .changedBy(actorId)
                        .reason("Khách yêu cầu đổi chuyên viên: " + (request.getNote() != null ? request.getNote() : ""))
                        .build();
                eventRepository.save(event);
            }

            changeRequest.setStatus(CaregiverChangeRequestStatus.APPROVED);
            changeRequest.setReviewedBy(actorId);
            changeRequest.setReviewedAt(now);

            BookingEvent requestApprovedEvent = BookingEvent.builder()
                    .booking(booking)
                    .eventType(BookingEventType.CAREGIVER_CHANGE_APPROVED)
                    .newCaregiverId(newCaregiver.getCaregiverId())
                    .changedBy(actorId)
                    .reason(request.getNote())
                    .build();
            eventRepository.save(requestApprovedEvent);

            return changeRequestRepository.save(changeRequest);
        });

        notificationService.notify(
                List.of(changeRequest.getCustomerUserId()),
                NotificationType.CAREGIVER_CHANGE_REQUEST_DECIDED,
                Map.of("body", "Trung tâm đã phê duyệt đổi người chăm sóc cho lịch " + booking.getBookingCode()),
                "BOOKING",
                booking.getId()
        );

        log.info("Caregiver change request {} approved by manager {}, new caregiver: {}", requestId, actorId, newCaregiver.getCaregiverId());
        return APIResponse.success("Phê duyệt đổi người chăm sóc thành công", mapper.toResponse(updated));
    }

    @Override
    public APIResponse<CaregiverChangeRequestResponse> rejectRequest(UUID actorId, UUID centerId, UUID requestId, CaregiverChangeRejectRequest request) {
        CaregiverChangeRequest changeRequest = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND));

        if (!Objects.equals(changeRequest.getCenterId(), centerId)) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND);
        }

        if (changeRequest.getStatus() != CaregiverChangeRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_PENDING);
        }

        Instant now = clock.instant();

        CaregiverChangeRequest updated = transactionTemplate.execute(status -> {
            changeRequest.setStatus(CaregiverChangeRequestStatus.REJECTED);
            changeRequest.setRejectionReason(request.getReason());
            changeRequest.setReviewedBy(actorId);
            changeRequest.setReviewedAt(now);

            BookingEvent event = BookingEvent.builder()
                    .booking(changeRequest.getBooking())
                    .eventType(BookingEventType.CAREGIVER_CHANGE_REJECTED)
                    .changedBy(actorId)
                    .reason(request.getReason())
                    .build();
            eventRepository.save(event);

            return changeRequestRepository.save(changeRequest);
        });

        notificationService.notify(
                List.of(changeRequest.getCustomerUserId()),
                NotificationType.CAREGIVER_CHANGE_REQUEST_DECIDED,
                Map.of("body", "Yêu cầu đổi người chăm sóc chưa thể thực hiện: " + request.getReason()),
                "BOOKING",
                changeRequest.getBooking().getId()
        );

        log.info("Caregiver change request {} rejected by manager {}", requestId, actorId);
        return APIResponse.success("Đã từ chối yêu cầu đổi người chăm sóc", mapper.toResponse(updated));
    }

    @Override
    public APIResponse<CaregiverChangeRequestResponse> cancelRequest(UUID actorId, UUID requestId) {
        CaregiverChangeRequest changeRequest = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND));

        if (!Objects.equals(changeRequest.getCustomerUserId(), actorId)) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_FOUND);
        }

        if (changeRequest.getStatus() != CaregiverChangeRequestStatus.PENDING) {
            throw new AppException(ErrorCode.CAREGIVER_CHANGE_REQUEST_NOT_PENDING);
        }

        changeRequest.setStatus(CaregiverChangeRequestStatus.CANCELLED);
        CaregiverChangeRequest saved = changeRequestRepository.save(changeRequest);

        log.info("Caregiver change request {} cancelled by customer {}", requestId, actorId);
        return APIResponse.success("Hủy yêu cầu đổi người chăm sóc thành công", mapper.toResponse(saved));
    }
}
