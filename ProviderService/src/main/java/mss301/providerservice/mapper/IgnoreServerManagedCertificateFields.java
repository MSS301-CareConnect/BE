package mss301.providerservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field của Certificate do server quyết định (kết quả xác minh, optimistic lock) — không bao giờ lấy từ request.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "verificationStatus", ignore = true)
@Mapping(target = "verificationReason", ignore = true)
@Mapping(target = "verifiedBy", ignore = true)
@Mapping(target = "verifiedAt", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface IgnoreServerManagedCertificateFields {
}
