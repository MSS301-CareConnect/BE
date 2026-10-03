package mss301.carerecipientservice.mapper;

import org.mapstruct.Mapping;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field riêng của {@code CareRecipient} do server quản lý — dùng CÙNG {@link IgnoreServerManagedFields} khi tạo/cập nhật
 * hồ sơ từ request: status (ACTIVE khi tạo, đổi qua API riêng), version (optimistic locking — client chỉ gửi để so
 * sánh ở service, không được ghi đè). createdBy/updatedBy do method truyền người thao tác vào (tham số).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
@Mapping(target = "status", ignore = true)
@Mapping(target = "version", ignore = true)
public @interface IgnoreServerManagedRecipientFields {
}
