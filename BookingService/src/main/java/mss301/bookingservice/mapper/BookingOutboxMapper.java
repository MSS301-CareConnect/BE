package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.response.OutboxEventResponse;
import mss301.bookingservice.entity.BookingOutbox;
import mss301.bookingservice.enums.OutboxEventType;
import mss301.bookingservice.enums.OutboxStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface BookingOutboxMapper {

    // Trạng thái ban đầu / số lần thử / hẹn gửi do BookingOutboxService quyết định rồi truyền vào.
    @IgnoreServerManagedFields
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "sentAt", ignore = true)
    BookingOutbox toOutbox(OutboxEventType eventType, UUID aggregateId, String idempotencyKey, String payload,
                           OutboxStatus status, Integer attempts, Instant nextAttemptAt);

    OutboxEventResponse toResponse(BookingOutbox outbox);
}
