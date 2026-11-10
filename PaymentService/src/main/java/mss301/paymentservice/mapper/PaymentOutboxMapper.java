package mss301.paymentservice.mapper;

import mss301.paymentservice.entity.PaymentOutbox;
import mss301.paymentservice.enums.OutboxEventType;
import mss301.paymentservice.enums.OutboxStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface PaymentOutboxMapper {

    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "attempts", constant = "0")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "sentAt", ignore = true)
    PaymentOutbox toOutbox(OutboxEventType eventType, UUID bookingId, UUID paymentId, String paymentReference,
                           OutboxStatus status, Instant nextAttemptAt);
}
