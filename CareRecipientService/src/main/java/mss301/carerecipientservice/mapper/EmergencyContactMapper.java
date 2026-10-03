package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface EmergencyContactMapper {
    EmergencyContactResponse toResponse(EmergencyContact emergencyContact);

    List<EmergencyContactResponse> toResponseList(List<EmergencyContact> emergencyContacts);

    // Create. Hồ sơ cha truyền qua @Context (không phải nguồn dữ liệu nên không lẫn fullName/phoneNumber của hồ sơ).
    // primary null → false; service phải bỏ cờ liên hệ chính cũ TRƯỚC khi lưu liên hệ chính mới.
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", expression = "java(recipient)")
    EmergencyContact toEmergencyContact(EmergencyContactRequest request, @Context CareRecipient recipient);

    // Tạo nhiều liên hệ cùng lúc với hồ sơ; danh sách null → rỗng.
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<EmergencyContact> toEmergencyContactList(List<EmergencyContactRequest> requests, @Context CareRecipient recipient);

    // PUT: thay toàn bộ (priority null → 0). primary do service xử lý (null = giữ nguyên, true phải bỏ cờ cũ trước).
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", ignore = true)
    @Mapping(target = "primary", ignore = true)
    @Mapping(target = "priority", defaultValue = "0")
    void update(EmergencyContactRequest request, @MappingTarget EmergencyContact emergencyContact);
}
