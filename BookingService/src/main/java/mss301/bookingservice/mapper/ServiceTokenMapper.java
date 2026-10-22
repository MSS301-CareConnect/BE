package mss301.bookingservice.mapper;

import mss301.bookingservice.client.dto.ServiceTokenRequest;
import mss301.bookingservice.config.BookingProperties;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceTokenMapper {

    // Danh tính máy của BookingService (BOOKING_SERVICE_CLIENT_ID / SECRET) → request xin token SERVICE ở Identity.
    ServiceTokenRequest toServiceTokenRequest(BookingProperties.ServiceClient serviceClient);
}
