package mss301.identityservice.service;

import mss301.identityservice.dto.request.ServiceTokenRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.ServiceTokenResponse;

import java.util.UUID;

public interface ServiceClientService {
    APIResponse<ServiceTokenResponse> issueToken(ServiceTokenRequest request);

    // Kiểm ở mỗi request mang token SERVICE: client còn tồn tại, đúng clientId và chưa bị tắt.
    boolean isActive(UUID clientUuid, String clientId);
}
