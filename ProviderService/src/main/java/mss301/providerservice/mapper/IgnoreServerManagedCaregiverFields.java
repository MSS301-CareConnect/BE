package mss301.providerservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field của CaregiverProfile do server quyết định (điểm đánh giá, kết quả xác minh, audit, optimistic lock, kỹ năng gán qua PUT /caregivers/{id}/services) —
 * không bao giờ lấy từ request.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "ratingAverage", ignore = true)
@Mapping(target = "ratingCount", ignore = true)
@Mapping(target = "verificationStatus", ignore = true)
@Mapping(target = "verificationReason", ignore = true)
@Mapping(target = "verifiedBy", ignore = true)
@Mapping(target = "verifiedAt", ignore = true)
@Mapping(target = "createdBy", ignore = true)
@Mapping(target = "updatedBy", ignore = true)
@Mapping(target = "version", ignore = true)
@Mapping(target = "skills", ignore = true)
public @interface IgnoreServerManagedCaregiverFields {
}
