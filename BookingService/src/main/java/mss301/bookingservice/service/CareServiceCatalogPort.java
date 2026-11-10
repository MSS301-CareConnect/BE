package mss301.bookingservice.service;

import mss301.bookingservice.dto.request.BookingServiceRequest;
import mss301.bookingservice.dto.response.ServiceQuote;

import java.util.List;
import java.util.UUID;

/**
 * Giá gốc của dịch vụ trung tâm từ CareCatalogService (POST /api/v1/center-services/quote — gói giá bắt buộc).
 * Không bao giờ tin giá từ client. Catalog lỗi → 503 CARE_CATALOG_UNAVAILABLE, không tạo / báo giá booking không giá.
 * Trả theo đúng thứ tự dịch vụ khách chọn; số lượng / thành tiền do BookingPriceCalculator tính.
 */
public interface CareServiceCatalogPort {
    List<ServiceQuote> quote(UUID centerId, List<BookingServiceRequest> services);
}
