package mss301.bookingservice.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.NotificationRequest;
import mss301.bookingservice.dto.request.NotificationDeletionRequest;
import mss301.bookingservice.dto.request.NotificationReadStateRequest;
import mss301.bookingservice.dto.response.APIResponse;
import mss301.bookingservice.dto.response.NotificationResponse;
import mss301.bookingservice.dto.response.NotificationsCreatedResponse;
import mss301.bookingservice.dto.response.NotificationsDeletedResponse;
import mss301.bookingservice.dto.response.NotificationsUpdatedResponse;
import mss301.bookingservice.dto.response.UnreadCountResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.enums.NotificationType;
import mss301.bookingservice.security.CurrentUserId;
import mss301.bookingservice.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Notifications", description = "Thông báo cá nhân và luồng realtime")
public class NotificationController {
    NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<PageResponse<NotificationResponse>> list(@CurrentUserId UUID actorId,
            @RequestParam(defaultValue = "false") boolean unreadOnly, @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(actorId, unreadOnly, type, page, size);
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<UnreadCountResponse> unreadCount(@CurrentUserId UUID actorId) {
        return notificationService.unreadCount(actorId);
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public SseEmitter stream(@CurrentUserId UUID actorId) {
        return notificationService.stream(actorId);
    }

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    public APIResponse<NotificationsCreatedResponse> create(@Valid @RequestBody NotificationRequest request) {
        return notificationService.create(request);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<Void> read(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return notificationService.read(actorId, id);
    }

    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<Void> readAll(@CurrentUserId UUID actorId) {
        return notificationService.readAll(actorId);
    }

    @PatchMapping("/{id}/unread")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<Void> unread(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return notificationService.unread(actorId, id);
    }

    @PatchMapping("/read-state")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<NotificationsUpdatedResponse> updateReadState(@CurrentUserId UUID actorId,
                                                                     @Valid @RequestBody NotificationReadStateRequest request) {
        return notificationService.updateReadState(actorId, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<Void> delete(@CurrentUserId UUID actorId, @PathVariable UUID id) {
        return notificationService.delete(actorId, id);
    }

    @PostMapping("/deletions")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<NotificationsDeletedResponse> deleteMany(@CurrentUserId UUID actorId,
                                                                @Valid @RequestBody NotificationDeletionRequest request) {
        return notificationService.deleteMany(actorId, request);
    }

    @DeleteMapping("/read")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_MANAGER','CUSTOMER','CAREGIVER')")
    public APIResponse<NotificationsDeletedResponse> deleteRead(@CurrentUserId UUID actorId) {
        return notificationService.deleteRead(actorId);
    }
}
