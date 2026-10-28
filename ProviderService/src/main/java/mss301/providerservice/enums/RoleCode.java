package mss301.providerservice.enums;

/**
 * Bản sao mã role của IdentityService (claim {@code roles} và {@code IdentityUserResponse.roles}) —
 * nguồn duy nhất cho mã role trong ProviderService, không viết chuỗi "CAREGIVER"/"CENTER_MANAGER" rải rác.
 */
public enum RoleCode {
    CUSTOMER,
    CAREGIVER,
    CENTER_MANAGER,
    ADMIN,
    SERVICE
}
