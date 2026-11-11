package mss301.identityservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
