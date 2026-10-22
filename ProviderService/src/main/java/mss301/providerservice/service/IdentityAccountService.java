package mss301.providerservice.service;

import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.enums.RoleCode;

import java.util.UUID;

public interface IdentityAccountService {

    /** Gọi mạng tới IdentityService — luôn gọi NGOÀI transaction ghi Provider DB. */
    IdentityUserResponse requireActiveRole(UUID userId, RoleCode requiredRole);
}
