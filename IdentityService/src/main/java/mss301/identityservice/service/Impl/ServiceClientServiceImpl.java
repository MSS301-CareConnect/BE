package mss301.identityservice.service.Impl;

import jakarta.servlet.http.HttpServletRequest;
import mss301.identityservice.security.ClientIpResolver;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.dto.request.ServiceTokenRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.ServiceTokenResponse;
import mss301.identityservice.entity.ServiceClient;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.AuthenticationMapper;
import mss301.identityservice.repository.ServiceClientRepository;
import mss301.identityservice.service.JwtService;
import mss301.identityservice.service.RateLimiter;
import mss301.identityservice.service.ServiceClientService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ServiceClientServiceImpl implements ServiceClientService {

    // Hash giả để clientId không tồn tại vẫn tốn cùng thời gian BCrypt — không lộ clientId nào có thật.
    static String DUMMY_SECRET_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z2Ld8qQ3h5Pr6VgG0ZhJu6hW";

    ServiceClientRepository serviceClientRepository;
    PasswordEncoder passwordEncoder;
    RateLimiter rateLimiter;
    JwtService jwtService;
    AuthenticationMapper authenticationMapper;
    IdentityProperties identityProperties;
    ClientIpResolver clientIpResolver;

    @Override
    @Transactional(readOnly = true)
    public APIResponse<ServiceTokenResponse> issueToken(ServiceTokenRequest request, HttpServletRequest httpServletRequest) {
        String clientId = request.getClientId().trim();
        String ip = clientIpResolver.resolve(httpServletRequest);
        rateLimiter.checkServiceTokenRateLimit(clientId, ip);
        ServiceClient serviceClient = verifyCredentials(clientId, request.getClientSecret(), ip);
        if (!serviceClient.isActive()) {
            log.warn("Disabled service client {} requested a token", clientId);
            throw new AppException(ErrorCode.SERVICE_CLIENT_DISABLED);
        }
        rateLimiter.resetServiceTokenAttempts(clientId, ip);
        Jwt accessToken = jwtService.issueServiceAccess(serviceClient.getId(), serviceClient.getClientId());
        ServiceTokenResponse serviceTokenResponse = authenticationMapper.toServiceTokenResponse(
                accessToken.getTokenValue(), identityProperties.getServiceTokenTtl().toSeconds());
        log.info("Service token issued for client {}", clientId);
        return APIResponse.success("Cấp token service thành công", serviceTokenResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID clientUuid, String clientId) {
        return serviceClientRepository.existsByIdAndClientIdAndActiveTrueAndDeletedAtIsNull(clientUuid, clientId);
    }

    // Sai clientId hay sai secret đều trả cùng một lỗi.
    private ServiceClient verifyCredentials(String clientId, String clientSecret, String ip) {
        Optional<ServiceClient> serviceClient = serviceClientRepository.findByClientIdAndDeletedAtIsNull(clientId);
        String secretHash = serviceClient.map(ServiceClient::getSecretHash).orElse(DUMMY_SECRET_HASH);
        boolean matches = passwordEncoder.matches(clientSecret, secretHash);
        if (serviceClient.isEmpty() || !matches) {
            rateLimiter.recordServiceTokenFailure(clientId, ip);
            log.warn("Invalid service client credentials for clientId={} from {}", clientId, ip);
            throw new AppException(ErrorCode.INVALID_CLIENT_CREDENTIALS);
        }
        return serviceClient.get();
    }
}
