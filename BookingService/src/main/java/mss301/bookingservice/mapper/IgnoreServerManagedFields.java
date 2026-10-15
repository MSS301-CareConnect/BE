package mss301.bookingservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field của BaseEntity do server quản lý — mapper tạo/cập nhật entity PHẢI bỏ qua: không cho client gán id/audit,
 * và không chép nhầm id của tham số nguồn khác (vd recipient.id) sang entity đích.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "deletedAt", ignore = true)
public @interface IgnoreServerManagedFields {
}
