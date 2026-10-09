package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.config.BookingProperties;
import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.mapper.BookingDetailMapper;
import mss301.bookingservice.service.CareServiceCatalogPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Bản TẠM khi chưa nối CareCatalogService (BOOKING_CATALOG_INTEGRATION_ENABLED=false): giữ id dịch vụ khách chọn,
 * tên / đơn vị / giá / tổng tiền đều NULL. Bật cờ khi chưa có adapter thật → từ chối khởi động thay vì âm thầm chạy tạm.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CareServiceCatalogPortImpl implements CareServiceCatalogPort {

    BookingDetailMapper bookingDetailMapper;

    public CareServiceCatalogPortImpl(BookingDetailMapper bookingDetailMapper, BookingProperties bookingProperties) {
        if (bookingProperties.getIntegration().isCatalogEnabled()) {
            throw new IllegalStateException("BOOKING_CATALOG_INTEGRATION_ENABLED=true but no CareCatalogService adapter exists yet");
        }
        this.bookingDetailMapper = bookingDetailMapper;
    }

    @Override
    public BookingQuote quote(UUID centerId, List<BookingServiceRequest> services, List<OccurrenceSlot> slots) {
        List<ServiceQuote> items = bookingDetailMapper.toUnpricedQuoteList(services);
        return BookingQuote.builder()
                .items(items)
                .build();
    }
}
