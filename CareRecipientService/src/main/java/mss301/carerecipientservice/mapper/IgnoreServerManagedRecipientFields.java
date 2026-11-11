package mss301.carerecipientservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "status", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface IgnoreServerManagedRecipientFields {
}
