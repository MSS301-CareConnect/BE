package mss301.providerservice.mapper;

import mss301.providerservice.dto.response.OutboxEventResponse;
import mss301.providerservice.entity.ProviderOutbox;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.client.dto.CenterCascadeRequest;
import mss301.providerservice.client.dto.CenterAccountsStatusRequest;
import mss301.providerservice.client.dto.SystemCancellationRequest;
import mss301.providerservice.client.dto.ServiceNotificationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface ProviderOutboxMapper {
    OutboxEventResponse toResponse(ProviderOutbox outbox);

    @IgnoreServerManagedFields
    @Mapping(target = "lastError", ignore = true)
    ProviderOutbox toProviderOutbox(UUID centerId, String eventType, Long sequenceNo, String payload,
                                   String status, Integer attempts, Instant nextAttemptAt);

    @Mapping(target = "centerId", source = "center.id")
    @Mapping(target = "caregiverId", ignore = true)
    CenterCascadeRequest toCascadeRequest(CareCenter center, String event, String reason);

    // H-11: caregiver ngừng hoạt động → Booking gắn cờ needsReassignment cho các buổi sắp tới.
    @Mapping(target = "managerUserId", ignore = true)
    @Mapping(target = "version", ignore = true)
    CenterCascadeRequest toCaregiverCascadeRequest(UUID centerId, UUID caregiverId, String event, String reason);

    CenterAccountsStatusRequest toAccountsStatusRequest(String status, String reason);

    @Mapping(target = "caregiverId", ignore = true)
    SystemCancellationRequest toCancellationRequest(UUID centerId, String reason, String note);

    @Mapping(target = "centerId", ignore = true)
    SystemCancellationRequest toCaregiverCancellationRequest(UUID caregiverId, String reason, String note);

    ServiceNotificationRequest toNotificationRequest(List<UUID> recipientUserIds, String type, String title, String body,
                                                     String refType, UUID refId);
}
