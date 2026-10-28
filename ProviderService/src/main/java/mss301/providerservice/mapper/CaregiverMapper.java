package mss301.providerservice.mapper;

import mss301.providerservice.client.dto.CenterServiceClientResponse;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.TimeOffRequest;
import mss301.providerservice.dto.request.TimeSlotRequest;
import mss301.providerservice.dto.response.AvailabilityCheckResponse;
import mss301.providerservice.dto.response.AvailabilityViolationResponse;
import mss301.providerservice.dto.response.AvailabilityWindowResponse;
import mss301.providerservice.dto.response.CaregiverCardResponse;
import mss301.providerservice.dto.response.CaregiverPublicResponse;
import mss301.providerservice.dto.response.CaregiverPublicServiceResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CaregiverSkillResponse;
import mss301.providerservice.dto.response.TimeOffResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSkill;
import mss301.providerservice.entity.CaregiverSkillId;
import mss301.providerservice.entity.CaregiverTimeOff;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.enums.AvailabilityViolationReason;
import mss301.providerservice.util.TimeWindow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class, uses = CertificateMapper.class, imports = CaregiverSkillId.class)
public interface CaregiverMapper {

    @Mapping(target = "centerId", source = "center.id")
    @Mapping(target = "centerName", source = "center.centerName")
    @Mapping(target = "centerStatus", source = "center.centerStatus")
    @Mapping(target = "careServiceIds", source = "skills", qualifiedByName = "toCareServiceIds")
    @Mapping(target = "centerServiceIds", source = "skills", qualifiedByName = "toCenterServiceIds")
    CaregiverResponse toResponse(CaregiverProfile caregiverProfile);

    List<CaregiverResponse> toResponseList(List<CaregiverProfile> caregiverProfiles);

    // center do service tra cứu + kiểm phạm vi rồi truyền vào; trạng thái mặc định và audit do service gán.
    @IgnoreServerManagedFields
    @IgnoreServerManagedCaregiverFields
    @Mapping(target = "caregiverStatus", ignore = true)
    CaregiverProfile toCaregiverProfile(CaregiverCreateRequest request, CareCenter center);

    // PUT toàn phần: null ghi đè. userId/center không đổi qua cập nhật hồ sơ.
    @IgnoreServerManagedFields
    @IgnoreServerManagedCaregiverFields
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "center", ignore = true)
    @Mapping(target = "caregiverStatus", ignore = true)
    void update(CaregiverUpdateRequest request, @MappingTarget CaregiverProfile caregiverProfile);

    // ─── Tìm kiếm / hồ sơ công khai (SRCH-01) — chỉ field khách được xem ───

    // avatarUrl (Media) và số chứng chỉ đã xác minh do service tra cứu rồi truyền vào.
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "careServiceIds", source = "caregiver.skills", qualifiedByName = "toCareServiceIds")
    CaregiverCardResponse toCardResponse(CaregiverProfile caregiver, String avatarUrl, long verifiedCertificateCount);

    // certificates: service chỉ truyền chứng chỉ VERIFIED.
    @Mapping(target = "caregiverId", source = "caregiver.id")
    @Mapping(target = "centerId", source = "caregiver.center.id")
    @Mapping(target = "centerName", source = "caregiver.center.centerName")
    @Mapping(target = "services", source = "caregiver.skills")
    CaregiverPublicResponse toPublicResponse(CaregiverProfile caregiver, List<Certificate> certificates,
                                             String avatarUrl);

    CaregiverPublicServiceResponse toPublicServiceResponse(CaregiverSkill caregiverSkill);

    // ─── Kỹ năng (caregiver_services) — entity con của aggregate CaregiverProfile ───

    @Mapping(target = "centerServiceId", source = "id.centerServiceId")
    CaregiverSkillResponse toSkillResponse(CaregiverSkill caregiverSkill);

    List<CaregiverSkillResponse> toSkillResponseList(List<CaregiverSkill> caregiverSkills);

    // Offering đã được service kiểm (cùng trung tâm, ACTIVE) qua Catalog; người gán + thời điểm do service truyền vào.
    @Mapping(target = "id", expression = "java(new CaregiverSkillId(caregiver.getId(), centerService.getId()))")
    @Mapping(target = "careServiceId", source = "centerService.catalogItem.id")
    @Mapping(target = "serviceName", source = "centerService.catalogItem.name")
    CaregiverSkill toCaregiverSkill(CaregiverProfile caregiver, CenterServiceClientResponse centerService,
                                    UUID assignedBy, Instant assignedAt);

    // ─── Ngày nghỉ + lịch rảnh (PRV-02) — entity con / giá trị dẫn xuất của aggregate CaregiverProfile ───

    @Mapping(target = "caregiverId", source = "caregiver.id")
    TimeOffResponse toTimeOffResponse(CaregiverTimeOff caregiverTimeOff);

    List<TimeOffResponse> toTimeOffResponseList(List<CaregiverTimeOff> caregiverTimeOffs);

    // caregiver do service tra cứu + kiểm phạm vi; createdBy lấy từ token (caregiver cũng có createdBy → chỉ rõ nguồn).
    @IgnoreServerManagedFields
    @Mapping(target = "createdBy", source = "createdBy")
    @Mapping(target = "version", ignore = true)
    CaregiverTimeOff toCaregiverTimeOff(TimeOffRequest request, CaregiverProfile caregiver, UUID createdBy);

    TimeWindow toTimeWindow(TimeSlotRequest timeSlotRequest);

    List<TimeWindow> toTimeWindowList(List<TimeSlotRequest> timeSlotRequests);

    AvailabilityWindowResponse toAvailabilityWindowResponse(TimeWindow timeWindow);

    List<AvailabilityWindowResponse> toAvailabilityWindowResponseList(List<TimeWindow> timeWindows);

    AvailabilityViolationResponse toAvailabilityViolationResponse(TimeWindow slot, AvailabilityViolationReason reason);

    AvailabilityCheckResponse toAvailabilityCheckResponse(boolean available,
                                                          List<AvailabilityViolationResponse> violations);

    // Caregiver chưa gán kỹ năng → [] (không null): Booking coi [] là "không làm được dịch vụ nào".
    @Named("toCareServiceIds")
    default List<UUID> toCareServiceIds(List<CaregiverSkill> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .map(CaregiverSkill::getCareServiceId)
                .distinct()
                .toList();
    }

    @Named("toCenterServiceIds")
    default List<UUID> toCenterServiceIds(List<CaregiverSkill> skills) {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .map(skill -> skill.getId().getCenterServiceId())
                .toList();
    }
}
