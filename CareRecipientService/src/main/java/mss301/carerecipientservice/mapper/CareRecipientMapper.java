package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.dto.request.CareRecipientCreateRequest;
import mss301.carerecipientservice.dto.request.CareRecipientUpdateRequest;
import mss301.carerecipientservice.dto.response.CareRecipientDetailResponse;
import mss301.carerecipientservice.dto.response.CareRecipientResponse;
import mss301.carerecipientservice.dto.response.CareRecipientSummaryResponse;
import mss301.carerecipientservice.dto.response.RecipientSnapshotResponse;
import mss301.carerecipientservice.entity.CareRecipient;
import mss301.carerecipientservice.entity.EmergencyContact;
import mss301.carerecipientservice.entity.Medication;
import mss301.carerecipientservice.entity.RecipientManager;
import mss301.carerecipientservice.enums.RecipientAccessLevel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class, uses = {MedicationMapper.class, EmergencyContactMapper.class})
public interface CareRecipientMapper {
    CareRecipientResponse toResponse(CareRecipient recipient);

    // myAccessLevel / medications / emergencyContacts: tham số trùng tên thuộc tính đích → MapStruct tự map.
    @Mapping(target = "profile", source = "recipient")
    CareRecipientDetailResponse toDetailResponse(CareRecipient recipient, RecipientAccessLevel myAccessLevel,
                                                 List<Medication> medications, List<EmergencyContact> emergencyContacts);

    // Admin / API nội bộ batch: không có quyền của người gọi.
    @Mapping(target = "age", expression = "java(calculateAge(recipient.getDateOfBirth()))")
    @Mapping(target = "myAccessLevel", ignore = true)
    @Mapping(target = "primaryManager", ignore = true)
    CareRecipientSummaryResponse toSummaryResponse(CareRecipient recipient);

    // Admin + API nội bộ /internal/care-recipients/batch.
    List<CareRecipientSummaryResponse> toSummaryResponseList(List<CareRecipient> recipients);

    // Danh sách của customer. id/createdAt có ở cả hai nguồn → chỉ rõ lấy từ hồ sơ.
    @Mapping(target = "id", source = "recipient.id")
    @Mapping(target = "createdAt", source = "recipient.createdAt")
    @Mapping(target = "age", expression = "java(calculateAge(recipient.getDateOfBirth()))")
    @Mapping(target = "myAccessLevel", source = "membership.accessLevel")
    @Mapping(target = "primaryManager", source = "membership.primary")
    CareRecipientSummaryResponse toSummaryResponse(CareRecipient recipient, RecipientManager membership);

    // API nội bộ /internal/care-recipients/{id}/snapshot. fullName/phoneNumber có ở cả hồ sơ lẫn liên hệ khẩn cấp
    // → chỉ rõ lấy từ hồ sơ; activeMedications/primaryEmergencyContact/snapshotAt: tham số trùng tên → tự map.
    @Mapping(target = "recipientId", source = "recipient.id")
    @Mapping(target = "fullName", source = "recipient.fullName")
    @Mapping(target = "phoneNumber", source = "recipient.phoneNumber")
    @Mapping(target = "age", expression = "java(calculateAge(recipient.getDateOfBirth()))")
    RecipientSnapshotResponse toSnapshotResponse(CareRecipient recipient, List<Medication> activeMedications,
                                                 EmergencyContact primaryEmergencyContact, Instant snapshotAt);

    // Create. status do service đặt (ACTIVE); người tạo vừa là createdBy vừa là updatedBy.
    @IgnoreServerManagedFields
    @IgnoreServerManagedRecipientFields
    @Mapping(target = "createdBy", source = "actorId")
    @Mapping(target = "updatedBy", source = "actorId")
    CareRecipient toCareRecipient(CareRecipientCreateRequest request, UUID actorId);

    // PUT: thay toàn bộ thông tin hồ sơ (null = xóa giá trị); version chỉ để service so sánh, không ghi đè.
    // updatedBy: tham số trùng tên → tự map; createdBy không đổi sau khi tạo.
    @IgnoreServerManagedFields
    @IgnoreServerManagedRecipientFields
    @Mapping(target = "createdBy", ignore = true)
    void update(CareRecipientUpdateRequest request, UUID updatedBy, @MappingTarget CareRecipient recipient);

    // Tuổi tròn theo ngày UTC (JVM chạy UTC); null khi chưa có ngày sinh.
    default Integer calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return Period.between(dateOfBirth, LocalDate.now(ZoneOffset.UTC)).getYears();
    }
}
