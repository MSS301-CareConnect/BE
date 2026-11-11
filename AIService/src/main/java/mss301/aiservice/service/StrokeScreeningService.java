package mss301.aiservice.service;

import mss301.aiservice.dto.request.StrokeScreeningRequest;
import mss301.aiservice.dto.response.StrokeScreeningResponse;

public interface StrokeScreeningService {
    StrokeScreeningResponse screen(StrokeScreeningRequest request);
}
