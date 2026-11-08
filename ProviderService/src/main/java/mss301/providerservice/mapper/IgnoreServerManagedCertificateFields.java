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
@Mapping(target = "centerVerifiedBy", ignore = true)
@Mapping(target = "centerVerifiedAt", ignore = true)
@Mapping(target = "centerVerificationReason", ignore = true)
@Mapping(target = "aiRiskLevel", ignore = true)
@Mapping(target = "aiWarnings", ignore = true)
@Mapping(target = "aiExtractedText", ignore = true)
@Mapping(target = "aiQrContent", ignore = true)
@Mapping(target = "aiCaregiverNameMatched", ignore = true)
@Mapping(target = "aiCertificateNameMatched", ignore = true)
@Mapping(target = "aiIssuedByMatched", ignore = true)
@Mapping(target = "aiCredentialNumberMatched", ignore = true)
@Mapping(target = "aiIssuedDateMatched", ignore = true)
@Mapping(target = "aiExpiryDateMatched", ignore = true)
@Mapping(target = "aiExpired", ignore = true)
@Mapping(target = "aiCheckedAt", ignore = true)
public @interface IgnoreServerManagedCertificateFields {
}
