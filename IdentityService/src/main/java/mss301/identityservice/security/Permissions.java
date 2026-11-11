package mss301.identityservice.security;

import java.util.List;

public final class Permissions {
    public static final String USER_READ = "user:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String MANAGER_CREATE = "manager:create";
    public static final String CAREGIVER_CREATE = "caregiver:create";
    public static final String ROLE_MANAGE = "role:manage";

    public static final List<String> ALL = List.of(USER_READ, USER_MANAGE, MANAGER_CREATE, CAREGIVER_CREATE, ROLE_MANAGE);

    // Lớp chỉ chứa hằng mã permission, không cho khởi tạo.
    private Permissions() {
    }
}
