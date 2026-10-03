package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.dto.request.MedicationRequest;
import mss301.carerecipientservice.dto.response.MedicationResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.Medication;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface MedicationMapper {
    MedicationResponse toResponse(Medication medication);

    List<MedicationResponse> toResponseList(List<Medication> medications);

    // Create. Hồ sơ cha + người thao tác truyền qua @Context để dùng được cho cả danh sách; active null → true.
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", expression = "java(recipient)")
    @Mapping(target = "updatedBy", expression = "java(updatedBy)")
    @Mapping(target = "active", defaultValue = "true")
    Medication toMedication(MedicationRequest request, @Context CareRecipient recipient, @Context UUID updatedBy);

    // Tạo nhiều thuốc cùng lúc với hồ sơ; danh sách null → rỗng.
    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<Medication> toMedicationList(List<MedicationRequest> requests, @Context CareRecipient recipient,
                                      @Context UUID updatedBy);

    // PUT: thay toàn bộ (null = xóa giá trị); "active" chỉ đổi qua PATCH /active. updatedBy: tham số trùng tên → tự map.
    @IgnoreServerManagedFields
    @Mapping(target = "recipient", ignore = true)
    @Mapping(target = "active", ignore = true)
    void update(MedicationRequest request, UUID updatedBy, @MappingTarget Medication medication);
}
