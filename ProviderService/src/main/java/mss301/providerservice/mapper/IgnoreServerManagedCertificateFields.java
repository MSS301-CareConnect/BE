package mss301.providerservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "verificationStatus", ignore = true)
@Mapping(target = "verificationReason", ignore = true)
@Mapping(target = "verifiedBy", ignore = true)
@Mapping(target = "verifiedAt", ignore = true)
@Mapping(target = "version", ignore = true)
@Mapping(target = "centerVerifiedBy", ignore = true)
@Mapping(target = "centerVerifiedAt", ignore = true)
@Mapping(target = "centerVerificationReason", ignore = true)
@Mapping(target = "aiCheckId", ignore = true)
@Mapping(target = "aiCheckStatus", ignore = true)
@Mapping(target = "aiCheckAttempts", ignore = true)
@Mapping(target = "aiNextAttemptAt", ignore = true)
@Mapping(target = "aiRiskLevel", ignore = true)
@Mapping(target = "aiWarnings", ignore = true)
@Mapping(target = "aiExtracted", ignore = true)
@Mapping(target = "aiModel", ignore = true)
@Mapping(target = "aiCheckedAt", ignore = true)
@Mapping(target = "aiLastError", ignore = true)
public @interface IgnoreServerManagedCertificateFields {
}
