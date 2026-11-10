package mss301.paymentservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Trường do JPA auditing / @Version quản lý — gắn trên mọi method tạo / cập nhật entity kế thừa BaseEntity.
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "createdAt", ignore = true)
@Mapping(target = "updatedAt", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface IgnoreServerManagedFields {
}
