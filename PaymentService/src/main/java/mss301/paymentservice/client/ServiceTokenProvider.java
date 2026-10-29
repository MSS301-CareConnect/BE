package mss301.paymentservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.dto.RemoteApiResponse;
import mss301.paymentservice.client.dto.ServiceTokenRequest;
import mss301.paymentservice.client.dto.ServiceTokenResponse;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.exception.AppException;
import mss301.paymentservice.exception.ErrorCode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Token SERVICE (client-credentials) của PaymentService, xin ở IdentityService qua gateway và cache tới
 * {@code exp − 60s} (mẫu BookingService). Thread-safe: chỉ một thread làm mới tại một thời điểm.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ServiceTokenProvider {
    static String SERVICE_TOKEN_PATH = "/api/v1/auth/service-token";
    static Duration REFRESH_BEFORE_EXPIRY = Duration.ofSeconds(60);
    static ParameterizedTypeReference<RemoteApiResponse<ServiceTokenResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    RestClient serviceTokenRestClient;
    PaymentProperties paymentProperties;
    Clock clock;

    @NonFinal
    volatile String cachedToken;

    @NonFinal
    volatile Instant refreshAt = Instant.MIN;

    public String getToken() {
        String token = cachedToken;
        if (token != null && clock.instant().isBefore(refreshAt)) {
            return token;
        }
        return refreshToken();
    }

    private synchronized String refreshToken() {
        // Thread khác có thể vừa làm mới trong lúc chờ khóa.
        if (cachedToken != null && clock.instant().isBefore(refreshAt)) {
            return cachedToken;
        }
        PaymentProperties.ServiceClient serviceClient = paymentProperties.getServiceClient();
        if (!serviceClient.isConfigured()) {
            log.error("PAYMENT_SERVICE_CLIENT_ID / PAYMENT_SERVICE_CLIENT_SECRET chưa cấu hình — không xin được token SERVICE");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        ServiceTokenResponse serviceTokenResponse = requestToken(serviceClient);
        cachedToken = serviceTokenResponse.getAccessToken();
        refreshAt = clock.instant().plusSeconds(serviceTokenResponse.getExpiresInSeconds()).minus(REFRESH_BEFORE_EXPIRY);
        log.info("Service token obtained for client {}", serviceClient.getClientId());
        return cachedToken;
    }

    private ServiceTokenResponse requestToken(PaymentProperties.ServiceClient serviceClient) {
        ServiceTokenRequest serviceTokenRequest = new ServiceTokenRequest(serviceClient.getClientId(),
                serviceClient.getClientSecret());
        RemoteApiResponse<ServiceTokenResponse> response;
        try {
            response = serviceTokenRestClient.post()
                    .uri(SERVICE_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(serviceTokenRequest)
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RestClientException e) {
            log.error("Cannot obtain service token for client {} ({})", serviceClient.getClientId(),
                    e.getClass().getSimpleName());
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null || response.getResult().getAccessToken() == null) {
            log.error("Identity returned an empty service token response");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        return response.getResult();
    }
}
