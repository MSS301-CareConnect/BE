package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.dto.request.RecipientAddressRequest;
import mss301.carerecipientservice.dto.response.RecipientAddressResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.RecipientAddress;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface RecipientAddressMapper {

    RecipientAddressResponse toResponse(RecipientAddress recipientAddress);

    List<RecipientAddressResponse> toResponseList(List<RecipientAddress> recipientAddresses);

    // Hồ sơ cha qua @Context (không lẫn các trường địa chỉ của chính hồ sơ).
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", expression = "java(recipient)")
    RecipientAddress toRecipientAddress(RecipientAddressRequest request, @Context CareRecipient recipient);

    // PUT: thay toàn bộ, null ghi đè.
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", ignore = true)
    void update(RecipientAddressRequest request, @MappingTarget RecipientAddress recipientAddress);
}
