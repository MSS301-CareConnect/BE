package mss301.carecatalogservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import mss301.carecatalogservice.client.dto.RemoteApiResponse;
import mss301.carecatalogservice.client.dto.ServiceTokenRequest;
import mss301.carecatalogservice.client.dto.ServiceTokenResponse;
import mss301.carecatalogservice.config.CareCatalogProperties;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import mss301.carecatalogservice.mapper.ServiceTokenMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

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
    CareCatalogProperties careCatalogProperties;
    ServiceTokenMapper serviceTokenMapper;
    Clock clock;

    @NonFinal
    volatile String cachedToken;

    @NonFinal
    volatile Instant refreshAt = Instant.MIN;

    // Lấy token SERVICE từ cache, gần hết hạn thì xin token mới.
    public String getToken() {
        String token = cachedToken;
        if (token != null && clock.instant().isBefore(refreshAt)) {
            return token;
        }
        return refreshToken();
    }

    // Xin token SERVICE mới từ IdentityService và cập nhật cache.
    private synchronized String refreshToken() {
        // Thread khác có thể vừa làm mới trong lúc chờ khóa.
        if (cachedToken != null && clock.instant().isBefore(refreshAt)) {
            return cachedToken;
        }
        CareCatalogProperties.ServiceClient serviceClient = careCatalogProperties.getServiceClient();
        if (!serviceClient.isConfigured()) {
            log.error("CARE_CATALOG_SERVICE_CLIENT_ID / CARE_CATALOG_SERVICE_CLIENT_SECRET chưa cấu hình — không xin được token SERVICE");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        ServiceTokenResponse serviceTokenResponse = requestToken(serviceClient);
        Instant now = clock.instant();
        cachedToken = serviceTokenResponse.getAccessToken();
        refreshAt = now.plusSeconds(serviceTokenResponse.getExpiresInSeconds()).minus(REFRESH_BEFORE_EXPIRY);
        log.info("Service token obtained for client {}", serviceClient.getClientId());
        return cachedToken;
    }

    // Gọi IdentityService đổi clientId / clientSecret lấy token SERVICE.
    private ServiceTokenResponse requestToken(CareCatalogProperties.ServiceClient serviceClient) {
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
            // Sai secret hay Identity sập đều là lỗi hạ tầng với người dùng cuối → 503, chi tiết nằm ở log.
            log.error("Cannot obtain service token for client {}", serviceClient.getClientId(), e);
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null || response.getResult().getAccessToken() == null) {
            log.error("Identity returned an empty service token response");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        return response.getResult();
    }
}
