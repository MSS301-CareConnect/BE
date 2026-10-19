package mss301.providerservice.service;

import mss301.providerservice.client.dto.IdentityUserResponse;

import java.util.UUID;

public interface IdentityAccountService {

    /** Gọi trước transaction ghi Provider DB. */
    IdentityUserResponse requireActiveRole(UUID userId, String requiredRole);
}
