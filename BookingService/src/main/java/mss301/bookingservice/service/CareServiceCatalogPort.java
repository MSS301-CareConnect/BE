package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.OccurrenceSlot;

import java.util.List;
import java.util.UUID;

/**
 * Báo giá dịch vụ của trung tâm (CareCatalogService: center_services + price_plans). Không bao giờ tin giá từ client.
 * TODO(CareCatalog): nối POST /internal/center-services/quote (docs/2026-10-09-PLAN_BOOKING.md §7.3); hiện là bản tạm
 * trả dòng dịch vụ chỉ có id, mọi giá NULL.
 */
public interface CareServiceCatalogPort {
    BookingQuote quote(UUID centerId, List<BookingServiceRequest> services, List<OccurrenceSlot> slots);
}
