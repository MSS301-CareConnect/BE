package mss301.identityservice.security;

import java.util.List;

/**
 * Permission code của IdentityService, dạng {@code <resource>:<action>}. Nguồn duy nhất — seed ở DataInitializer,
 * kiểm ở @PreAuthorize và service.
 */
public final class Permissions {
    public static final String USER_READ = "user:read";
    public static final String USER_MANAGE = "user:manage";
    public static final String MANAGER_CREATE = "manager:create";
    public static final String CAREGIVER_CREATE = "caregiver:create";
    public static final String ROLE_MANAGE = "role:manage";

    public static final List<String> ALL = List.of(USER_READ, USER_MANAGE, MANAGER_CREATE, CAREGIVER_CREATE, ROLE_MANAGE);

    private Permissions() {
    }
}
