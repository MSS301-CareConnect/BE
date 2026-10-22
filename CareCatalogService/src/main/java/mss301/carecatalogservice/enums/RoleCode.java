package mss301.carecatalogservice.enums;

// Bản sao role code trong claim roles[] của IdentityService (contract, không import class service khác).
// SERVICE = token client-credentials của service khác (POST /api/v1/auth/service-token).
public enum RoleCode {
    CUSTOMER,
    CAREGIVER,
    CENTER_MANAGER,
    ADMIN,
    SERVICE
}
