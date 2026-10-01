package mss301.identityservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Các field của {@code User} do server quản lý — mapper tạo User từ request PHẢI bỏ qua để chống
 * mass assignment (id/audit, password hash, status, centerId, authzVersion, role).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "deletedAt", ignore = true)
@Mapping(target = "password", ignore = true)
@Mapping(target = "status", ignore = true)
@Mapping(target = "centerId", ignore = true)
@Mapping(target = "authzVersion", ignore = true)
@Mapping(target = "userRoles", ignore = true)
public @interface IgnoreServerManagedUserFields {
}
