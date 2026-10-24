package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Notification;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import mss301.bookingservice.mapper.NotificationMapper;
import mss301.bookingservice.repository.NotificationRepository;
import mss301.bookingservice.service.NotificationService;
import mss301.bookingservice.util.NotificationTemplates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Validator;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    NotificationRepository notificationRepository;
    NotificationMapper notificationMapper;
    TransactionTemplate transactionTemplate;
    Validator validator;
    Clock clock;
    ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<NotificationResponse>> list(UUID userId, boolean unreadOnly, NotificationType type, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
        Page<Notification> notifications = notificationRepository.findAll((root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("recipientUserId"), userId));
            predicates.add(builder.isNull(root.get("deletedAt")));
            if (unreadOnly) {
                predicates.add(builder.isNull(root.get("readAt")));
            }
            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PageResponse<NotificationResponse> response = PageResponse.of(notifications,
                notificationMapper.toResponseList(notifications.getContent()));
        return APIResponse.success("Lấy thông báo thành công", response);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<UnreadCountResponse> unreadCount(UUID userId) {
        UnreadCountResponse response = notificationMapper.toUnreadCountResponse(
                notificationRepository.countByRecipientUserIdAndReadAtIsNullAndDeletedAtIsNull(userId));
        return APIResponse.success("Lấy số thông báo chưa đọc thành công", response);
    }

    @Override
    @Transactional
    public APIResponse<Void> read(UUID userId, UUID id) {
        Notification notification = notificationRepository.findByIdAndRecipientUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return APIResponse.success("Đã đọc thông báo");
    }

    @Override
    @Transactional
    public APIResponse<Void> readAll(UUID userId) {
        notificationRepository.markAllRead(userId, clock.instant());
        return APIResponse.success("Đã đọc tất cả thông báo");
    }

    @Override
    public APIResponse<NotificationsCreatedResponse> create(NotificationRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        List<NotificationResponse> responses = persist(request);
        List<UUID> recipients = request.getRecipientUserIds().stream().distinct().toList();
        for (int i = 0; i < recipients.size(); i++) {
            emit(recipients.get(i), "notification", responses.get(i));
        }
        NotificationsCreatedResponse response = notificationMapper.toCreatedResponse((long) responses.size());
        return APIResponse.success("Gửi thông báo thành công", response);
    }

    @Override
    public void notify(List<UUID> userIds, NotificationType type, Map<String, Object> params, String refType, UUID refId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<UUID> recipients = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        String title = NotificationTemplates.title(type);
        String body = params != null && params.get("body") instanceof String text ? text : title;
        NotificationRequest request = notificationMapper.toRequest(recipients, type, title, body, refType, refId);
        afterCommit(() -> {
            try {
                create(request);
            } catch (RuntimeException exception) {
                log.error("Không lưu được thông báo loại {} cho {} người nhận", type, recipients.size(), exception);
            }
        });
    }

    @Override
    public SseEmitter stream(UUID userId) {
        SseEmitter emitter = new SseEmitter(1_800_000L);
        emitters.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> emitters.computeIfPresent(userId, (id, current) -> {
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(error -> cleanup.run());
        emit(userId, "heartbeat", clock.instant().toString());
        return emitter;
    }

    @Override
    public void heartbeat() {
        emitters.keySet().forEach(userId -> emit(userId, "heartbeat", clock.instant().toString()));
    }

    @Override
    public void publishSessionUpdate(UUID userId, Object payload) {
        afterCommit(() -> emit(userId, "session-update", payload));
    }

    private List<NotificationResponse> persist(NotificationRequest request) {
        // Callback afterCommit vẫn có resource cũ; transaction mới bảo đảm INSERT được commit thật.
        TransactionTemplate write = new TransactionTemplate(transactionTemplate.getTransactionManager());
        write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return write.execute(status -> notificationMapper.toResponseList(notificationRepository.saveAllAndFlush(
                request.getRecipientUserIds().stream().distinct()
                        .map(userId -> notificationMapper.toNotification(request, userId)).toList())));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void emit(UUID userId, String event, Object payload) {
        List<SseEmitter> current = emitters.get(userId);
        if (current == null) {
            return;
        }
        for (SseEmitter emitter : current) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (IOException | IllegalStateException exception) {
                emitter.complete();
                current.remove(emitter);
            }
        }
    }
}
