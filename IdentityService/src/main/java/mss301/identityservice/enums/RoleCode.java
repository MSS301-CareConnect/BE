package mss301.identityservice.enums;

// 4 role nền tảng — giá trị lưu ở roles.role_code và claim roles[] của JWT.
// SERVICE chỉ có trong token client-credentials (service_clients), KHÔNG seed vào bảng roles và không gán cho user.
public enum RoleCode {
    CUSTOMER,
    CAREGIVER,
    CENTER_MANAGER,
    ADMIN,
    SERVICE
}
