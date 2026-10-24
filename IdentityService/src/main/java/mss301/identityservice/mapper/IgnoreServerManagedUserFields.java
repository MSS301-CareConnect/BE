package mss301.identityservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field riêng của {@code User} do server quản lý — dùng CÙNG {@link IgnoreServerManagedFields} khi tạo User từ request
 * để chống mass assignment: password hash (nhạy cảm, service tự gán), status, centerId (lấy theo actor), authzVersion,
 * role.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "password", ignore = true)
@Mapping(target = "status", ignore = true)
@Mapping(target = "centerId", ignore = true)
@Mapping(target = "authzVersion", ignore = true)
@Mapping(target = "userRoles", ignore = true)
@Mapping(target = "statusReason", ignore = true)
@Mapping(target = "lockSource", ignore = true)
public @interface IgnoreServerManagedUserFields {
}
