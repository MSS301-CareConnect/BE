package mss301.paymentservice.enums;

// Role trong claim roles[] của access token (IdentityService) + SERVICE của token client-credentials.
public enum RoleCode {
    CUSTOMER,
    CAREGIVER,
    CENTER_MANAGER,
    ADMIN,
    SERVICE
}
