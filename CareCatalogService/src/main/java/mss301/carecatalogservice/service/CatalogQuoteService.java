package mss301.carecatalogservice.service;

import mss301.carecatalogservice.dto.request.InternalServiceQuoteRequest;
import mss301.carecatalogservice.dto.response.InternalServiceQuoteResponse;

import java.util.List;

public interface CatalogQuoteService {
    List<InternalServiceQuoteResponse> quote(InternalServiceQuoteRequest request);
}
