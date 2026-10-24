package mss301.providerservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.client.dto.ServiceTokenRequest;
import mss301.providerservice.client.dto.ServiceTokenResponse;
import mss301.providerservice.config.ProviderProperties;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ServiceTokenMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Token SERVICE (client-credentials, C1.2) của ProviderService để gọi endpoint cần quyền service của service khác
 * (vd Catalog {@code GET /center-services/{id}}). Xin token ở IdentityService qua gateway
 * ({@code POST /api/v1/auth/service-token}), cache tới khi còn dưới {@link #REFRESH_BEFORE_EXPIRY} rồi xin lại.
 * Thread-safe: chỉ một thread làm mới tại một thời điểm. Dùng RestClient riêng
 * ({@code ProviderConfig.serviceTokenRestClient}, không Feign) để không đi qua interceptor của chính nó.
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
    ProviderProperties providerProperties;
    ServiceTokenMapper serviceTokenMapper;
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
        ProviderProperties.ServiceClient serviceClient = providerProperties.getServiceClient();
        if (!serviceClient.isConfigured()) {
            log.error("PROVIDER_SERVICE_CLIENT_ID / PROVIDER_SERVICE_CLIENT_SECRET chưa cấu hình — không xin được token SERVICE");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        ServiceTokenResponse serviceTokenResponse = requestToken(serviceClient);
        Instant now = clock.instant();
        cachedToken = serviceTokenResponse.getAccessToken();
        refreshAt = now.plusSeconds(serviceTokenResponse.getExpiresInSeconds()).minus(REFRESH_BEFORE_EXPIRY);
        log.info("Service token obtained for client {}", serviceClient.getClientId());
        return cachedToken;
    }

    private ServiceTokenResponse requestToken(ProviderProperties.ServiceClient serviceClient) {
        ServiceTokenRequest serviceTokenRequest = serviceTokenMapper.toServiceTokenRequest(serviceClient);
        RemoteApiResponse<ServiceTokenResponse> response;
        try {
            response = serviceTokenRestClient.post()
                    .uri(SERVICE_TOKEN_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(serviceTokenRequest)
                    .retrieve()
                    .body(RESPONSE_TYPE);
        } catch (RestClientException e) {
            // Sai secret (401) hay Identity sập đều là lỗi hạ tầng với người dùng cuối → 503, chi tiết nằm ở log.
            log.error("Cannot obtain service token for client {}", serviceClient.getClientId(), e);
            if (e instanceof RestClientResponseException responseException
                    && (responseException.getStatusCode().value() == 401 || responseException.getStatusCode().value() == 403)) {
                throw new AppException(ErrorCode.REMOTE_COMMAND_REJECTED);
            }
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null || response.getResult().getAccessToken() == null) {
            log.error("Identity returned an empty service token response");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        return response.getResult();
    }
}
