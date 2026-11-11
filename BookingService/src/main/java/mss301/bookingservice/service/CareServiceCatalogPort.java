package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.ServiceQuote;

import java.util.List;
import java.util.UUID;

public interface CareServiceCatalogPort {
    // Báo giá dịch vụ của trung tâm qua CareCatalogService.
    List<ServiceQuote> quote(UUID centerId, List<BookingServiceRequest> services);
}
