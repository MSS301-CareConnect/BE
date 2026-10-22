package mss301.bookingservice.mapper;

import mss301.bookingservice.client.dto.CareCatalogQuoteRequest;
import mss301.bookingservice.client.dto.CareCatalogQuoteResponse;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.BookingItemResponse;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface BookingDetailMapper {

    BookingItemResponse toResponse(BookingDetail detail);

    List<BookingItemResponse> toResponseList(List<BookingDetail> details);

    // Dòng dịch vụ tạo cùng booking, từ báo giá đã tính (không trực tiếp từ request).
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BookingDetail> toBookingDetailList(List<ServiceQuote> quotes, @Context Booking booking);

    // BookingDetail không kế thừa BaseEntity và chỉ có method tạo này → ignore id / createdAt tại chỗ.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "booking", expression = "java(booking)")
    BookingDetail toBookingDetail(ServiceQuote quote, @Context Booking booking);

    // Request báo giá gửi CareCatalogService: chỉ id dịch vụ / gói khách chọn.
    @Mapping(target = "items", source = "services")
    CareCatalogQuoteRequest toCatalogQuoteRequest(UUID centerId, List<BookingServiceRequest> services);

    // Giá gốc từ Catalog; số lượng / thành tiền do BookingPriceCalculator tính sau.
    @Mapping(target = "unitType", source = "pricingUnit")
    @Mapping(target = "unitPrice", source = "basePrice")
    @Mapping(target = "planDurationMinutes", source = "durationMinutes")
    @Mapping(target = "unitsPerOccurrence", ignore = true)
    @Mapping(target = "occurrenceCount", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    ServiceQuote toServiceQuote(CareCatalogQuoteResponse catalogQuote);
}
