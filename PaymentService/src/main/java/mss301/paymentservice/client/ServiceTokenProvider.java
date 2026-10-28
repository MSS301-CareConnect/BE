package mss301.paymentservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.dto.request.ServiceTokenRequest;
import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.exception.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.time.*;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceTokenProvider {
    final RestClient serviceTokenRestClient;
    final PaymentProperties properties;
    final Clock clock;
    volatile String cachedToken;
    volatile Instant refreshAt = Instant.MIN;

    public String getToken() {
        if (cachedToken != null && clock.instant().isBefore(refreshAt))
            return cachedToken;

        return refresh();
    }

    private synchronized String refresh() {
        if (cachedToken != null && clock.instant().isBefore(refreshAt)) {
            return cachedToken;
        }

        var client = properties.getServiceClient();
        if (!client.isConfigured()) {
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }

        try {
            APIResponse<ServiceTokenResponse> body = serviceTokenRestClient.post().uri("/api/v1/auth/service-token")
                    .contentType(MediaType.APPLICATION_JSON).body(new ServiceTokenRequest(client.getClientId(), client.getClientSecret()))
                    .retrieve().body(new ParameterizedTypeReference<>() {});

            if (body == null || body.getResult() == null || body.getResult().getAccessToken() == null){
                throw new RestClientException("empty token");
            }
            cachedToken = body.getResult().getAccessToken();
            refreshAt = clock.instant().plusSeconds(Math.max(30, body.getResult().getExpiresInSeconds() - 60));
            return cachedToken;

        } catch (RestClientException e) {
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
    }
}
