package mss301.providerservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field của WorkShift do server quyết định (audit, optimistic lock) — không bao giờ lấy từ request.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "updatedBy", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface IgnoreServerManagedWorkShiftFields {
}
