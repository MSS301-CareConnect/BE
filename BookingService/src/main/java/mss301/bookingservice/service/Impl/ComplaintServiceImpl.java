package mss301.bookingservice.service.Impl;

import java.util.Comparator;
import java.time.Instant;
import mss301.bookingservice.config.BookingProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.ComplaintRequest;
import mss301.bookingservice.dto.request.ComplaintAdminResolutionRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.ComplaintResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Complaint;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.enums.ComplaintAction;
import mss301.bookingservice.enums.ComplaintStatus;
import mss301.bookingservice.enums.ComplaintLevel;
import mss301.bookingservice.enums.ComplaintTargetType;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.RoleCode;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.ComplaintMapper;
import mss301.bookingservice.repository.ComplaintRepository;
import mss301.bookingservice.repository.ComplaintEventRepository;
import mss301.bookingservice.repository.BookingRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.service.ComplaintService;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.service.BookingNotificationService;
import mss301.bookingservice.service.BookingOutboxService;
import mss301.bookingservice.client.PaymentServiceClient;
import mss301.bookingservice.client.dto.PaymentPageResponse;
import mss301.bookingservice.client.dto.PaymentSummaryResponse;
import mss301.bookingservice.client.dto.RemoteApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ComplaintServiceImpl implements ComplaintService {
    ComplaintRepository complaintRepository;
    ComplaintEventRepository complaintEventRepository;
    BookingRepository bookingRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    ComplaintMapper complaintMapper;
    NotificationService notificationService;
    BookingNotificationService bookingNotificationService;
    BookingOutboxService bookingOutboxService;
    PaymentServiceClient paymentServiceClient;
    TransactionTemplate transactionTemplate;
    BookingProperties bookingProperties;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<ComplaintResponse>> list(UUID actorId, Set<RoleCode> roles, UUID scopeCenterId,
            ComplaintStatus status, ComplaintLevel level, UUID centerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Page<Complaint> complaints = complaintRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (!roles.contains(RoleCode.ADMIN)) {
                List<Predicate> scope = new ArrayList<>();
                if (roles.contains(RoleCode.CUSTOMER)) {
                    scope.add(builder.equal(root.get("customerId"), actorId));
                }
                if (roles.contains(RoleCode.CENTER_MANAGER) && scopeCenterId != null) {
                    scope.add(builder.equal(root.get("centerId"), scopeCenterId));
                }
                predicates.add(builder.or(scope.toArray(Predicate[]::new)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (level != null) {
                predicates.add(builder.equal(root.get("level"), level));
            }
            if (centerId != null) {
                predicates.add(builder.equal(root.get("centerId"), centerId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PageResponse<ComplaintResponse> response = PageResponse.of(complaints, complaints.getContent().stream()
                .map(complaint -> complaintMapper.toResponse(complaint, List.of())).toList());
        return APIResponse.success("Lấy danh sách khiếu nại thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<ComplaintResponse> get(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id) {
        Complaint complaint = complaintRepository.findByIdAndDeletedAtIsNull(id)
                .filter(found -> canRead(actorId, roles, centerId, found))
                .orElseThrow(() -> new AppException(ErrorCode.COMPLAINT_NOT_FOUND));
        ComplaintResponse response = toResponse(complaint);
        return APIResponse.success("Lấy khiếu nại thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<ComplaintResponse> create(UUID actorId, ComplaintRequest request) {
        // Khóa booking: hai yêu cầu cùng lúc không mở được hai khiếu nại cho cùng một buổi (D20).
        Booking booking = bookingRepository.findForUpdate(request.getBookingId())
                .filter(found -> found.getDeletedAt() == null)
                .filter(found -> actorId.equals(found.getCustomerId()))
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
        if (!Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED, BookingStatus.CANCELLED)
                .contains(booking.getStatus())) {
            throw new AppException(ErrorCode.COMPLAINT_BOOKING_INVALID);
        }
        UUID caregiverId = booking.getCaregiverId();
        if (request.getOccurrenceId() != null) {
            BookingOccurrence occurrence = bookingOccurrenceRepository.findWithBooking(request.getOccurrenceId())
                    .filter(found -> found.getBooking().getId().equals(booking.getId()))
                    .orElseThrow(() -> new AppException(ErrorCode.COMPLAINT_BOOKING_INVALID));
            caregiverId = occurrence.getCaregiverId();
            checkWindow(occurrence.getStartAt(), occurrence.getEndAt());
            if (complaintRepository.existsByOccurrenceIdAndStatusInAndDeletedAtIsNull(occurrence.getId(), ComplaintStatus.ACTIVE)) {
                throw new AppException(ErrorCode.COMPLAINT_ALREADY_OPEN);
            }
        } else {
            checkBookingWindow(booking);
            if (complaintRepository.existsByBookingIdAndOccurrenceIdIsNullAndStatusInAndDeletedAtIsNull(booking.getId(),
                    ComplaintStatus.ACTIVE)) {
                throw new AppException(ErrorCode.COMPLAINT_ALREADY_OPEN);
            }
        }
        String code = generateCode();
        Complaint complaint = complaintMapper.toComplaint(request, booking, caregiverId, code);
        boolean centerTarget = request.getTargetType() == ComplaintTargetType.CENTER;
        complaint.setStatus(centerTarget ? ComplaintStatus.ESCALATED : ComplaintStatus.OPEN);
        complaint.setLevel(centerTarget ? ComplaintLevel.ADMIN : ComplaintLevel.CENTER);
        if (centerTarget) {
            complaint.setEscalatedAt(clock.instant());
            complaint.setEscalateReason(request.getContent());
        }
        Complaint saved = complaintRepository.saveAndFlush(complaint);
        recordEvent(saved, actorId, ComplaintAction.CREATED, null, request.getContent());
        if (centerTarget) {
            bookingNotificationService.notifyAdmins(NotificationType.COMPLAINT_UPDATED, "COMPLAINT", saved.getId());
        }
        ComplaintResponse response = toResponse(saved);
        return APIResponse.success("Gửi khiếu nại thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<ComplaintResponse> transition(UUID actorId, Set<RoleCode> roles, UUID centerId, UUID id,
                                                    ComplaintAction action, String note) {
        Complaint complaint = complaintRepository.findForUpdate(id)
                .filter(found -> canRead(actorId, roles, centerId, found))
                .orElseThrow(() -> new AppException(ErrorCode.COMPLAINT_NOT_FOUND));
        ComplaintStatus from = complaint.getStatus();
        boolean manager = roles.contains(RoleCode.CENTER_MANAGER) && complaint.getCenterId().equals(centerId);
        boolean customer = roles.contains(RoleCode.CUSTOMER) && complaint.getCustomerId().equals(actorId);
        switch (action) {
            case ACCEPT -> {
                checkTransition(manager && complaint.getLevel() == ComplaintLevel.CENTER && from == ComplaintStatus.OPEN);
                complaint.setStatus(ComplaintStatus.IN_PROGRESS);
            }
            case RESOLVE -> {
                checkTransition(manager && complaint.getLevel() == ComplaintLevel.CENTER && from == ComplaintStatus.IN_PROGRESS);
                checkNote(note);
                complaint.setStatus(ComplaintStatus.RESOLVED);
                complaint.setResolution(note.trim());
                complaint.setResolvedBy(actorId);
                complaint.setResolvedAt(clock.instant());
            }
            case ESCALATE -> {
                boolean pending = complaint.getLevel() == ComplaintLevel.CENTER
                        && (from == ComplaintStatus.OPEN || from == ComplaintStatus.IN_PROGRESS);
                checkTransition((manager && pending) || (customer && (pending || from == ComplaintStatus.RESOLVED)));
                // D20: khách chỉ vượt cấp khiếu nại chưa giải quyết khi trung tâm đã để quá 48h.
                if (customer && pending && clock.instant().isBefore(complaint.getCreatedAt()
                        .plus(bookingProperties.getComplaint().getCustomerEscalateAfter()))) {
                    throw new AppException(ErrorCode.COMPLAINT_ESCALATE_TOO_EARLY);
                }
                checkNote(note);
                complaint.setStatus(ComplaintStatus.ESCALATED);
                complaint.setLevel(ComplaintLevel.ADMIN);
                complaint.setEscalatedAt(clock.instant());
                complaint.setEscalateReason(note.trim());
            }
            case WITHDRAW -> {
                // D20: khách rút khi khiếu nại còn chờ xử lý (chưa RESOLVED / RESOLVED_BY_ADMIN).
                checkTransition(customer && ComplaintStatus.ACTIVE.contains(from));
                complaint.setStatus(ComplaintStatus.WITHDRAWN);
            }
            default -> throw new AppException(ErrorCode.INVALID_COMPLAINT_TRANSITION);
        }
        complaintRepository.flush();
        recordEvent(complaint, actorId, action, from, note);
        if (action == ComplaintAction.ESCALATE) {
            bookingNotificationService.notifyAdmins(NotificationType.COMPLAINT_UPDATED, "COMPLAINT", complaint.getId());
        }
        ComplaintResponse response = toResponse(complaint);
        return APIResponse.success("Cập nhật khiếu nại thành công", response);
    }

    @Override
    public APIResponse<ComplaintResponse> adminResolve(UUID actorId, UUID id, ComplaintAdminResolutionRequest request) {
        Complaint snapshot = complaintRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.COMPLAINT_NOT_FOUND));
        checkTransition(snapshot.getStatus() == ComplaintStatus.ESCALATED);
        BigDecimal amount = request.getRefundAmount() == null ? BigDecimal.ZERO : request.getRefundAmount();
        if (amount.signum() < 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        checkNote(request.getResolution());
        if (amount.signum() > 0 && amount.compareTo(findRefundableAmount(snapshot.getBookingId())) > 0) {
            throw new AppException(ErrorCode.REFUND_AMOUNT_EXCEEDS_PAID);
        }
        ComplaintResponse response = transactionTemplate.execute(status -> {
            Complaint complaint = complaintRepository.findForUpdate(id)
                    .orElseThrow(() -> new AppException(ErrorCode.COMPLAINT_NOT_FOUND));
            checkTransition(complaint.getStatus() == ComplaintStatus.ESCALATED
                    && complaint.getVersion().equals(snapshot.getVersion()));
            complaint.setStatus(ComplaintStatus.RESOLVED_BY_ADMIN);
            complaint.setResolution(request.getResolution().trim());
            complaint.setResolvedAt(clock.instant());
            complaint.setResolvedBy(actorId);
            complaint.setRefundAmount(amount);
            if (amount.signum() > 0) {
                bookingOutboxService.enqueue(OutboxEventType.COMPLAINT_REFUND, id, "COMPLAINT_REFUND:" + id,
                        complaintMapper.toRefundRequest(complaint.getBookingId(), id, amount, actorId, request.getResolution()));
            }
            complaintRepository.flush();
            recordEvent(complaint, actorId, ComplaintAction.ADMIN_RESOLVE, ComplaintStatus.ESCALATED, request.getResolution());
            return toResponse(complaint);
        });
        return APIResponse.success("Admin đã giải quyết khiếu nại", response);
    }

    private BigDecimal findRefundableAmount(UUID bookingId) {
        BigDecimal refundable = BigDecimal.ZERO;
        int page = 0;
        int totalPages;
        do {
            RemoteApiResponse<PaymentPageResponse> response = paymentServiceClient.listPayments(bookingId, "SUCCESS", page, 100);
            if (response == null || response.getResult() == null || response.getResult().getItems() == null) {
                throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
            }
            for (PaymentSummaryResponse payment : response.getResult().getItems()) {
                if (!bookingId.equals(payment.getBookingId()) || !"SUCCESS".equals(payment.getStatus())
                        || payment.getAmount() == null || payment.getRefundedAmount() == null) {
                    throw new AppException(ErrorCode.PAYMENT_SERVICE_UNAVAILABLE);
                }
                // M-09: trừ cả khoản hoàn đang chờ — Payment cũng chặn như vậy, tránh outbox COMPLAINT_REFUND FAILED.
                BigDecimal reserved = payment.getReservedRefundAmount() == null ? BigDecimal.ZERO : payment.getReservedRefundAmount();
                refundable = refundable.add(payment.getAmount()).subtract(payment.getRefundedAmount()).subtract(reserved);
            }
            totalPages = response.getResult().getTotalPages();
            page++;
        } while (page < totalPages);
        return refundable.max(BigDecimal.ZERO);
    }

    private void recordEvent(Complaint complaint, UUID actorId, ComplaintAction action, ComplaintStatus from, String note) {
        complaintEventRepository.saveAndFlush(complaintMapper.toComplaintEvent(complaint.getId(), actorId, action,
                from, complaint.getStatus(), note));
        notificationService.notify(List.of(complaint.getCustomerId()), NotificationType.COMPLAINT_UPDATED,
                Map.of(), "COMPLAINT", complaint.getId());
        bookingNotificationService.notifyCenter(complaint.getCenterId(), NotificationType.COMPLAINT_UPDATED,
                "COMPLAINT", complaint.getId());
    }

    private ComplaintResponse toResponse(Complaint complaint) {
        return complaintMapper.toResponse(complaint, complaintMapper.toEventResponseList(
                complaintEventRepository.findByComplaintIdAndDeletedAtIsNullOrderByCreatedAtAsc(complaint.getId())));
    }

    private boolean canRead(UUID actorId, Set<RoleCode> roles, UUID centerId, Complaint complaint) {
        return roles.contains(RoleCode.ADMIN)
                || (roles.contains(RoleCode.CUSTOMER) && actorId.equals(complaint.getCustomerId()))
                || (roles.contains(RoleCode.CENTER_MANAGER) && complaint.getCenterId().equals(centerId));
    }

    // D20: buổi đã bắt đầu và chưa quá 7 ngày sau giờ kết thúc.
    private void checkWindow(Instant startAt, Instant endAt) {
        Instant now = clock.instant();
        if (startAt.isAfter(now) || now.isAfter(endAt.plus(bookingProperties.getComplaint().getOpenWindow()))) {
            throw new AppException(ErrorCode.COMPLAINT_WINDOW_CLOSED);
        }
    }

    // Khiếu nại cấp booking: tính theo buổi đã diễn ra gần nhất; booking hủy trước khi có buổi nào → tính từ lúc hủy.
    private void checkBookingWindow(Booking booking) {
        Instant now = clock.instant();
        Instant lastEnd = bookingOccurrenceRepository.findAllByBooking(booking.getId()).stream()
                .filter(occurrence -> !occurrence.getStartAt().isAfter(now))
                .map(BookingOccurrence::getEndAt)
                .max(Comparator.naturalOrder())
                .orElse(booking.getStatus() == BookingStatus.CANCELLED ? booking.getCancelledAt() : null);
        if (lastEnd == null || now.isAfter(lastEnd.plus(bookingProperties.getComplaint().getOpenWindow()))) {
            throw new AppException(ErrorCode.COMPLAINT_WINDOW_CLOSED);
        }
    }

    private void checkTransition(boolean allowed) {
        if (!allowed) {
            throw new AppException(ErrorCode.INVALID_COMPLAINT_TRANSITION);
        }
    }

    private void checkNote(String note) {
        if (note == null || note.isBlank() || note.length() > 4000) {
            throw new AppException(ErrorCode.RESOLUTION_REQUIRED);
        }
    }

    private String generateCode() {
        String date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = "CP-" + date + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
            if (!complaintRepository.existsByComplaintCode(code)) {
                return code;
            }
        }
        throw new AppException(ErrorCode.CONCURRENT_UPDATE);
    }
}
