package mss301.identityservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field của BaseEntity do server quản lý — mọi method tạo/cập nhật entity kế thừa BaseEntity PHẢI có annotation này:
 * không cho client gán id/audit và không chép nhầm id của tham số nguồn khác sang entity đích.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "deletedAt", ignore = true)
public @interface IgnoreServerManagedFields {
}
