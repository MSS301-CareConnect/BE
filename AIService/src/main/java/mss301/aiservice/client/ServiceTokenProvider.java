package mss301.aiservice.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import mss301.aiservice.client.dto.RemoteApiResponse;
import mss301.aiservice.client.dto.ServiceTokenRequest;
import mss301.aiservice.client.dto.ServiceTokenResponse;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.mapper.ServiceTokenMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ServiceTokenProvider {
    static String SERVICE_TOKEN_PATH = "/api/v1/auth/service-token";

    static Duration REFRESH_BEFORE_EXPIRY = Duration.ofSeconds(60);

    static ParameterizedTypeReference<RemoteApiResponse<ServiceTokenResponse>> RESPONSE_TYPE = new ParameterizedTypeReference<>() {};

    RestClient serviceTokenRestClient;

    AIProperties aiProperties;

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
        if (cachedToken != null && clock.instant().isBefore(refreshAt)) {
            return cachedToken;
        }

        AIProperties.ServiceClient serviceClient = aiProperties.getServiceClient();

        if (!serviceClient.isConfigured()) {
            log.error("AI service client is not configured");

            throw new AppException(ErrorCode.SERVICE_TOKEN_UNAVAILABLE);
        }

        ServiceTokenResponse tokenResponse = requestToken(serviceClient);
        Instant now = clock.instant();

        cachedToken = tokenResponse.getAccessToken();
        long expiresInSeconds = tokenResponse.getExpiresInSeconds();

        refreshAt = now.plusSeconds(expiresInSeconds).minus(REFRESH_BEFORE_EXPIRY);

        log.info("Service token obtained for client {}", serviceClient.getClientId());
        return cachedToken;
    }

    private ServiceTokenResponse requestToken(
            AIProperties.ServiceClient serviceClient
    ) {
        ServiceTokenRequest request =
                serviceTokenMapper
                        .toServiceTokenRequest(
                                serviceClient
                        );

        RemoteApiResponse<ServiceTokenResponse> response;

        try {
            response =
                    serviceTokenRestClient
                            .post()
                            .uri(SERVICE_TOKEN_PATH)
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(request)
                            .retrieve()
                            .body(RESPONSE_TYPE);
        } catch (RestClientResponseException exception) {
            int status =
                    exception
                            .getStatusCode()
                            .value();

            if (status == 401 || status == 403) {
                log.error(
                        "Identity rejected AI service credentials"
                );

                throw new AppException(
                        ErrorCode.REMOTE_SERVICE_REJECTED
                );
            }

            log.error(
                    "Identity service token request failed with HTTP {}",
                    status
            );

            throw new AppException(
                    ErrorCode.SERVICE_TOKEN_UNAVAILABLE
            );
        } catch (RestClientException exception) {
            log.error(
                    "Cannot connect to IdentityService ({})",
                    exception
                            .getClass()
                            .getSimpleName()
            );

            throw new AppException(
                    ErrorCode.SERVICE_TOKEN_UNAVAILABLE
            );
        }

        if (
                response == null
                        || response.getResult() == null
                        || response
                        .getResult()
                        .getAccessToken() == null
                        || response
                        .getResult()
                        .getAccessToken()
                        .isBlank()
        ) {
            throw new AppException(
                    ErrorCode.SERVICE_TOKEN_UNAVAILABLE
            );
        }

        return response.getResult();
    }
}
