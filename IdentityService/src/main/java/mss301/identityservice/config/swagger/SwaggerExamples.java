package mss301.identityservice.config.swagger;

/**
 * Mọi chuỗi ví dụ JSON cho @ExampleObject trên controller (chỉ constants).
 */
public final class SwaggerExamples {

    // ===== Auth =====
    public static final String LOGIN_REQUEST = """
            {
              "email": "customer@example.com",
              "password": "CareConnect9!"
            }
            """;

    public static final String REFRESH_REQUEST = """
            {
              "refreshToken": "eyJraWQiOiJjYXJlY29ubmVjdC1pZGVudGl0eS0yMDI2LTA5Ii..."
            }
            """;

    // ===== Account =====
    public static final String REGISTER_REQUEST = """
            {
              "email": "customer@example.com",
              "password": "CareConnect9!",
              "fullName": "Nguyễn Văn A",
              "phoneNumber": "0901234567"
            }
            """;

    public static final String VERIFY_OTP_REQUEST = """
            {
              "email": "customer@example.com",
              "otpCode": "123456"
            }
            """;

    public static final String EMAIL_REQUEST = """
            {
              "email": "customer@example.com"
            }
            """;

    public static final String RESET_PASSWORD_REQUEST = """
            {
              "email": "customer@example.com",
              "otpCode": "123456",
              "newPassword": "NewPassword9!"
            }
            """;

    public static final String UPDATE_PROFILE_REQUEST = """
            {
              "fullName": "Nguyễn Văn B",
              "phoneNumber": "0907654321"
            }
            """;

    public static final String CHANGE_PASSWORD_REQUEST = """
            {
              "currentPassword": "CareConnect9!",
              "newPassword": "NewPassword9!"
            }
            """;

    public static final String PROVISION_MANAGER_REQUEST = """
            {
              "email": "manager@center.example.com",
              "fullName": "Trần Thị Quản Lý",
              "phoneNumber": "0912345678",
              "roleCode": "CENTER_MANAGER",
              "centerId": "3f6c2a1e-8b7d-4c5e-9f10-2a3b4c5d6e7f"
            }
            """;

    public static final String PROVISION_CAREGIVER_REQUEST = """
            {
              "email": "caregiver@center.example.com",
              "fullName": "Lê Văn Chăm Sóc",
              "phoneNumber": "0987654321",
              "roleCode": "CAREGIVER"
            }
            """;

    // ===== Role =====
    public static final String ROLE_REQUEST = """
            {
              "roleCode": "SUPPORT_STAFF",
              "roleName": "Nhân viên hỗ trợ",
              "description": "Role bổ sung chỉ xem tài khoản"
            }
            """;

    public static final String ROLE_PERMISSIONS_REQUEST = """
            {
              "permissionIds": ["8a1f5c2e-1d3b-4e6f-9a7c-0b2d4f6e8a1c"]
            }
            """;

    private SwaggerExamples() {
    }
}
