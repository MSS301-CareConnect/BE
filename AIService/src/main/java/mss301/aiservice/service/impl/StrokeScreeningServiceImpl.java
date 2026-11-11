package mss301.aiservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.client.StrokeModelClient;
import mss301.aiservice.client.dto.StrokeModelClientRequest;
import mss301.aiservice.dto.request.StrokeScreeningRequest;
import mss301.aiservice.dto.response.StrokeScreeningResponse;
import mss301.aiservice.service.StrokeScreeningService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StrokeScreeningServiceImpl implements StrokeScreeningService {
    StrokeModelClient strokeModelClient;

    @Override
    public StrokeScreeningResponse screen(StrokeScreeningRequest request) {
        StrokeModelClientRequest clientRequest = StrokeModelClientRequest.builder()
                        .hypertension(request.getHypertension())
                        .highCholesterol(request.getHighCholesterol())
                        .myocardialInfarctionHistory(request.getMyocardialInfarctionHistory())
                        .anginaOrChdHistory(request.getAnginaOrChdHistory())
                        .diabetes(request.getDiabetes())
                        .sex(request.getSex().name())
                        .age(request.getAge())
                        .bmi(request.getBmi())
                        .currentSmoker(request.getCurrentSmoker())
                        .build();

        return strokeModelClient.predict(clientRequest);
    }
}
