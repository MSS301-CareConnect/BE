package mss301.carerecipientservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import mss301.carerecipientservice.client.dto.IdentityApiResponse;
import mss301.carerecipientservice.client.dto.ServiceTokenRequest;
import mss301.carerecipientservice.client.dto.ServiceTokenResponse;
import mss301.carerecipientservice.config.CareRecipientProperties;
import mss301.carerecipientservice.exception.AppException;
import mss301.carerecipientservice.exception.ErrorCode;
import mss301.carerecipientservice.mapper.ServiceTokenMapper;
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
    static ParameterizedTypeReference<IdentityApiResponse<ServiceTokenResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    RestClient serviceTokenRestClient;
    CareRecipientProperties careRecipientProperties;
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
        CareRecipientProperties.ServiceClient serviceClient = careRecipientProperties.getServiceClient();
        if (!serviceClient.isConfigured()) {
            log.error("CARE_RECIPIENT_SERVICE_CLIENT_ID / CARE_RECIPIENT_SERVICE_CLIENT_SECRET chưa cấu hình — không xin được token SERVICE");
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
    private ServiceTokenResponse requestToken(CareRecipientProperties.ServiceClient serviceClient) {
        ServiceTokenRequest serviceTokenRequest = serviceTokenMapper.toServiceTokenRequest(serviceClient);
        IdentityApiResponse<ServiceTokenResponse> response;
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
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        if (response == null || response.getResult() == null || response.getResult().getAccessToken() == null) {
            log.error("Identity returned an empty service token response");
            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }
        return response.getResult();
    }
}
