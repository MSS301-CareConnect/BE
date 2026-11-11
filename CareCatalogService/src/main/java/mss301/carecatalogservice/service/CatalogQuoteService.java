package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;

import java.util.List;

public interface CatalogQuoteService {
    // Tính báo giá các dịch vụ theo gói giá của trung tâm.
    List<ServiceQuoteResponse> quote(ServiceQuoteRequest request);
}
