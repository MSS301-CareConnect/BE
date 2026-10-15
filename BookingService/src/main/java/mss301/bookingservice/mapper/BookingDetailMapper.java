package mss301.bookingservice.mapper;

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

@Mapper(config = MapStructConfig.class)
public interface BookingDetailMapper {

    BookingItemResponse toResponse(BookingDetail detail);

    List<BookingItemResponse> toResponseList(List<BookingDetail> details);

    // Dòng dịch vụ tạo cùng booking, từ báo giá đã chuẩn hóa (không trực tiếp từ request).
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BookingDetail> toBookingDetailList(List<ServiceQuote> quotes, @Context Booking booking);

    // BookingDetail không kế thừa BaseEntity và chỉ có method tạo này → ignore id / createdAt tại chỗ.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "booking", expression = "java(booking)")
    BookingDetail toBookingDetail(ServiceQuote quote, @Context Booking booking);

    // Báo giá chưa có giá (TODO CareCatalog): chỉ giữ id khách chọn — tên / đơn vị / giá không bao giờ lấy từ client.
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<ServiceQuote> toUnpricedQuoteList(List<BookingServiceRequest> services);

    @Mapping(target = "serviceName", ignore = true)
    @Mapping(target = "unitType", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    ServiceQuote toUnpricedQuote(BookingServiceRequest service);
}
