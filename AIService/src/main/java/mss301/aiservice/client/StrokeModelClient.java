package mss301.aiservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.aiservice.client.dto.StrokeModelClientRequest;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.dto.response.StrokeScreeningResponse;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class StrokeModelClient {
    private static final String PREDICT_PATH =
            "/api/v1/stroke-screening/predict";

    private final RestClient restClient;

    public StrokeModelClient(AIProperties aiProperties) {
        AIProperties.StrokeModel properties = aiProperties.getStrokeModel();

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public StrokeScreeningResponse predict(StrokeModelClientRequest request) {
        Map<String, Object> requestBody = createRequestBody(request);

        try {
            StrokeScreeningResponse response = restClient.post()
                            .uri(PREDICT_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .body(requestBody)
                            .retrieve()
                            .body(StrokeScreeningResponse.class);

            if (response == null) {
                log.error("Stroke model returned an empty response");
                throw new AppException(ErrorCode.STROKE_MODEL_UNAVAILABLE);
            }

            return response;

        } catch (AppException exception) {
            throw exception;

        } catch (ResourceAccessException exception) {
            log.error("Could not connect to StrokeModelService", exception);
            throw new AppException(ErrorCode.STROKE_MODEL_UNAVAILABLE);

        } catch (RestClientResponseException exception) {
            log.error("StrokeModelService returned status {} with body: {}", exception.getStatusCode(),
                    exception.getResponseBodyAsString());

            throw new AppException(ErrorCode.STROKE_MODEL_UNAVAILABLE);

        } catch (RestClientException exception) {
            log.error("Unexpected error while calling StrokeModelService", exception);
            throw new AppException(ErrorCode.STROKE_MODEL_UNAVAILABLE);
        }
    }

    private Map<String, Object> createRequestBody(StrokeModelClientRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("hypertension", request.getHypertension());
        body.put("high_cholesterol", request.getHighCholesterol());
        body.put("myocardial_infarction_history", request.getMyocardialInfarctionHistory());
        body.put("angina_or_chd_history", request.getAnginaOrChdHistory());
        body.put("diabetes", request.getDiabetes());

        body.put("sex", request.getSex());
        body.put("age", request.getAge());
        body.put("bmi", request.getBmi());
        body.put("current_smoker", request.getCurrentSmoker());

        return body;
    }
}
