package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.ServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.ServiceQuoteResponse;

import java.util.List;

public interface CatalogQuoteService {
    List<ServiceQuoteResponse> quote(ServiceQuoteRequest request);
}
