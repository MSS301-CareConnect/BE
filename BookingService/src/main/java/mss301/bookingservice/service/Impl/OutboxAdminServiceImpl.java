package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.enums.OutboxStatus;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.BookingOutboxMapper;
import mss301.bookingservice.repository.BookingOutboxRepository;
import mss301.bookingservice.service.OutboxAdminService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OutboxAdminServiceImpl implements OutboxAdminService {
    static int MAX_NOTE_LENGTH = 400;

    BookingOutboxRepository bookingOutboxRepository;
    BookingOutboxMapper bookingOutboxMapper;
    Clock clock;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<OutboxEventResponse>> list(OutboxStatus status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<BookingOutbox> rows = status == null
                ? bookingOutboxRepository.findByDeletedAtIsNull(pageRequest)
                : bookingOutboxRepository.findByStatusAndDeletedAtIsNull(status, pageRequest);
        PageResponse<OutboxEventResponse> response = PageResponse.of(rows,
                rows.getContent().stream().map(bookingOutboxMapper::toResponse).toList());
        return APIResponse.success("Lấy danh sách outbox thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> requeue(UUID actorId, UUID id) {
        BookingOutbox outbox = lockFailed(id);
        outbox.setStatus(OutboxStatus.PENDING);
        outbox.setAttempts(0);
        outbox.setNextAttemptAt(clock.instant());
        bookingOutboxRepository.flush();
        log.warn("Admin {} requeued outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã xếp lại việc outbox", bookingOutboxMapper.toResponse(outbox));
    }

    @Override
    @Transactional
    public APIResponse<OutboxEventResponse> skip(UUID actorId, UUID id, String note) {
        BookingOutbox outbox = lockFailed(id);
        outbox.setStatus(OutboxStatus.SKIPPED);
        String detail = "Admin xử lý tay: " + note.trim();
        outbox.setLastError(detail.length() <= MAX_NOTE_LENGTH ? detail : detail.substring(0, MAX_NOTE_LENGTH));
        bookingOutboxRepository.flush();
        log.warn("Admin {} skipped outbox {} ({})", actorId, id, outbox.getEventType());
        return APIResponse.success("Đã bỏ qua việc outbox", bookingOutboxMapper.toResponse(outbox));
    }

    // Khóa dòng (SKIP LOCKED): dòng đang được scheduler gửi thì báo xung đột, admin thử lại sau.
    private BookingOutbox lockFailed(UUID id) {
        if (!bookingOutboxRepository.existsById(id)) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FOUND);
        }
        BookingOutbox outbox = bookingOutboxRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONCURRENT_UPDATE));
        if (outbox.getStatus() != OutboxStatus.FAILED) {
            throw new AppException(ErrorCode.OUTBOX_NOT_FAILED);
        }
        return outbox;
    }
}
