package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.CertificateResponse;
import mss301.providerservice.dto.response.ScheduleResponse;
import mss301.providerservice.dto.response.ShiftRegistrationResponse;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.entity.CaregiverSchedule;
import mss301.providerservice.entity.Certificate;
import mss301.providerservice.entity.ShiftRegistration;
import mss301.providerservice.entity.WorkShift;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.Gender;
import mss301.providerservice.enums.RegistrationStatus;
import mss301.providerservice.enums.ScheduleStatus;
import mss301.providerservice.enums.ShiftStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.enums.CertificateVerificationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ProviderMapperTests {

    CaregiverMapper caregiverMapper = new CaregiverMapperImpl(new CertificateMapperImpl());
    CertificateMapper certificateMapper = new CertificateMapperImpl();
    WorkShiftMapper workShiftMapper = new WorkShiftMapperImpl();
    ShiftRegistrationMapper shiftRegistrationMapper = new ShiftRegistrationMapperImpl();
    CaregiverScheduleMapper caregiverScheduleMapper = new CaregiverScheduleMapperImpl();

    @Test
    void caregiverMappingFlattensCenterProperties() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = CareCenter.builder()
                .centerName("Trung tâm An Khang")
                .centerStatus(CenterStatus.ACTIVE)
                .build();
        center.setId(centerId);

        UUID caregiverId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CaregiverProfile profile = CaregiverProfile.builder()
                .userId(userId)
                .center(center)
                .fullName("Nguyễn Văn A")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Kinh nghiệm chăm sóc người cao tuổi")
                .ratingAverage(BigDecimal.valueOf(4.8))
                .ratingCount(12)
                .verificationStatus(VerificationStatus.VERIFIED)
                .caregiverStatus(CaregiverStatus.ACTIVE)
                .version(1L)
                .build();
        profile.setId(caregiverId);

        CaregiverResponse response = caregiverMapper.toResponse(profile);

        assertEquals(caregiverId, response.getId());
        assertEquals(caregiverId, response.getCaregiverId());
        assertEquals(userId, response.getUserId());
        assertEquals(centerId, response.getCenterId());
        assertEquals("Trung tâm An Khang", response.getCenterName());
        assertEquals(CenterStatus.ACTIVE, response.getCenterStatus());
        assertEquals("Nguyễn Văn A", response.getFullName());
        assertEquals(Gender.MALE, response.getGender());
        assertEquals(5, response.getExperienceYears());
        assertEquals(VerificationStatus.VERIFIED, response.getVerificationStatus());
        assertEquals(CaregiverStatus.ACTIVE, response.getCaregiverStatus());
    }

    @Test
    void certificateMappingFlattensCaregiverId() {
        UUID caregiverId = UUID.randomUUID();
        CaregiverProfile caregiver = new CaregiverProfile();
        caregiver.setId(caregiverId);

        UUID certId = UUID.randomUUID();
        Certificate cert = Certificate.builder()
                .caregiver(caregiver)
                .certificateName("Chứng chỉ sơ cấp cứu")
                .issuedBy("Hội Chữ Thập Đỏ")
                .credentialNumber("RED-CROSS-12345")
                .issuedDate(LocalDate.of(2023, 1, 15))
                .expiryDate(LocalDate.of(2026, 1, 15))
                .verificationStatus(CertificateVerificationStatus.ADMIN_VERIFIED)
                .version(0L)
                .build();
        cert.setId(certId);

        CertificateResponse response = certificateMapper.toResponse(cert);

        assertEquals(certId, response.getId());
        assertEquals(caregiverId, response.getCaregiverId());
        assertEquals("Chứng chỉ sơ cấp cứu", response.getCertificateName());
        assertEquals("Hội Chữ Thập Đỏ", response.getIssuedBy());
        assertEquals("RED-CROSS-12345", response.getCredentialNumber());
        assertEquals(CertificateVerificationStatus.ADMIN_VERIFIED, response.getVerificationStatus());
    }

    @Test
    void shiftMappingCombinesWorkShiftAndApprovedCount() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = CareCenter.builder().centerName("Trung tâm Bình An").build();
        center.setId(centerId);

        UUID shiftId = UUID.randomUUID();
        WorkShift shift = WorkShift.builder()
                .center(center)
                .workDate(LocalDate.of(2026, 11, 1))
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(12, 0))
                .shiftType("MORNING")
                .shiftStatus(ShiftStatus.OPEN)
                .capacity(3)
                .version(2L)
                .build();
        shift.setId(shiftId);

        WorkShiftResponse response = workShiftMapper.toResponse(shift, 2L);

        assertEquals(shiftId, response.getId());
        assertEquals(centerId, response.getCenterId());
        assertEquals("Trung tâm Bình An", response.getCenterName());
        assertEquals(LocalDate.of(2026, 11, 1), response.getWorkDate());
        assertEquals(LocalTime.of(8, 0), response.getStartTime());
        assertEquals(LocalTime.of(12, 0), response.getEndTime());
        assertEquals(ShiftStatus.OPEN, response.getShiftStatus());
        assertEquals(3, response.getCapacity());
        assertEquals(2L, response.getApprovedCount());
    }

    @Test
    void registrationMappingFlattensShiftAndCaregiver() {
        UUID shiftId = UUID.randomUUID();
        WorkShift shift = new WorkShift();
        shift.setId(shiftId);

        UUID caregiverId = UUID.randomUUID();
        CaregiverProfile caregiver = CaregiverProfile.builder().fullName("Trần Thị B").build();
        caregiver.setId(caregiverId);

        UUID regId = UUID.randomUUID();
        ShiftRegistration reg = ShiftRegistration.builder()
                .workShift(shift)
                .caregiver(caregiver)
                .registrationStatus(RegistrationStatus.APPROVED)
                .caregiverNote("Đã sẵn sàng")
                .version(0L)
                .build();
        reg.setId(regId);

        ShiftRegistrationResponse response = shiftRegistrationMapper.toResponse(reg);

        assertEquals(regId, response.getId());
        assertEquals(shiftId, response.getWorkShiftId());
        assertEquals(caregiverId, response.getCaregiverId());
        assertEquals("Trần Thị B", response.getCaregiverName());
        assertEquals(RegistrationStatus.APPROVED, response.getRegistrationStatus());
    }

    @Test
    void scheduleMappingFlattensShiftCenterAndCaregiver() {
        UUID centerId = UUID.randomUUID();
        CareCenter center = CareCenter.builder().centerName("Trung tâm Hy Vọng").build();
        center.setId(centerId);

        UUID shiftId = UUID.randomUUID();
        WorkShift shift = WorkShift.builder()
                .center(center)
                .workDate(LocalDate.of(2026, 12, 1))
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(17, 0))
                .build();
        shift.setId(shiftId);

        UUID caregiverId = UUID.randomUUID();
        CaregiverProfile caregiver = CaregiverProfile.builder().fullName("Lê Văn C").build();
        caregiver.setId(caregiverId);

        UUID regId = UUID.randomUUID();
        ShiftRegistration reg = new ShiftRegistration();
        reg.setId(regId);

        UUID scheduleId = UUID.randomUUID();
        UUID assignedBy = UUID.randomUUID();
        CaregiverSchedule schedule = CaregiverSchedule.builder()
                .registration(reg)
                .caregiver(caregiver)
                .workShift(shift)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .assignedBy(assignedBy)
                .assignedAt(Instant.now())
                .version(1L)
                .build();
        schedule.setId(scheduleId);

        ScheduleResponse response = caregiverScheduleMapper.toResponse(schedule);

        assertEquals(scheduleId, response.getId());
        assertEquals(regId, response.getRegistrationId());
        assertEquals(caregiverId, response.getCaregiverId());
        assertEquals("Lê Văn C", response.getCaregiverName());
        assertEquals(shiftId, response.getWorkShiftId());
        assertEquals(centerId, response.getCenterId());
        assertEquals(LocalDate.of(2026, 12, 1), response.getWorkDate());
        assertEquals(LocalTime.of(13, 0), response.getStartTime());
        assertEquals(LocalTime.of(17, 0), response.getEndTime());
        assertEquals(ScheduleStatus.SCHEDULED, response.getScheduleStatus());
        assertEquals(assignedBy, response.getAssignedBy());
    }

    @Test
    void caregiverCreateUsesGivenCenterAndSkipsServerManagedFields() {
        CareCenter center = CareCenter.builder().centerName("Trung tâm An Khang").build();
        center.setId(UUID.randomUUID());
        UUID userId = UUID.randomUUID();
        CaregiverCreateRequest request = CaregiverCreateRequest.builder()
                .userId(userId)
                .fullName("Nguyễn Văn A")
                .gender(Gender.MALE)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .experienceYears(5)
                .bio("Chăm sóc người cao tuổi")
                .build();

        CaregiverProfile result = caregiverMapper.toCaregiverProfile(request, center);

        assertSame(center, result.getCenter());
        assertEquals(userId, result.getUserId());
        assertEquals("Nguyễn Văn A", result.getFullName());
        assertEquals(5, result.getExperienceYears());
        // id của center không được chép sang hồ sơ; trạng thái / điểm / audit do service quyết định.
        assertNull(result.getId());
        assertNull(result.getCreatedAt());
        assertNull(result.getVerificationStatus());
        assertNull(result.getCaregiverStatus());
        assertNull(result.getRatingAverage());
        assertNull(result.getRatingCount());
        assertNull(result.getCreatedBy());
        assertNull(result.getUpdatedBy());
        assertNull(result.getVersion());
    }

    @Test
    void workShiftUpdateOverwritesWithNullAndKeepsCenterAndAudit() {
        CareCenter center = new CareCenter();
        UUID creatorId = UUID.randomUUID();
        WorkShift shift = WorkShift.builder()
                .center(center)
                .note("Ghi chú cũ")
                .registrationDeadline(Instant.now())
                .createdBy(creatorId)
                .version(4L)
                .build();
        WorkShiftRequest request = WorkShiftRequest.builder()
                .workDate(LocalDate.of(2026, 12, 2))
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(11, 0))
                .shiftType("MORNING")
                .shiftStatus(ShiftStatus.OPEN)
                .capacity(4)
                .version(99L)
                .build();

        workShiftMapper.update(request, shift);

        assertEquals(LocalDate.of(2026, 12, 2), shift.getWorkDate());
        assertEquals(4, shift.getCapacity());
        assertNull(shift.getNote());
        assertNull(shift.getRegistrationDeadline());
        assertSame(center, shift.getCenter());
        assertEquals(creatorId, shift.getCreatedBy());
        assertEquals(4L, shift.getVersion());
    }

    @Test
    void scheduleCreateUsesLockedShiftAndRegistrationCaregiver() {
        CaregiverProfile caregiver = new CaregiverProfile();
        caregiver.setId(UUID.randomUUID());
        WorkShift staleShift = new WorkShift();
        WorkShift lockedShift = WorkShift.builder().note("Ghi chú của ca").build();
        ShiftRegistration registration = ShiftRegistration.builder()
                .workShift(staleShift)
                .caregiver(caregiver)
                .version(3L)
                .build();
        registration.setId(UUID.randomUUID());
        UUID assignedBy = UUID.randomUUID();

        CaregiverSchedule result = caregiverScheduleMapper.toCaregiverSchedule(registration, lockedShift, assignedBy,
                "Ghi chú phân công");

        assertSame(registration, result.getRegistration());
        assertSame(caregiver, result.getCaregiver());
        assertSame(lockedShift, result.getWorkShift());
        assertEquals(assignedBy, result.getAssignedBy());
        assertEquals("Ghi chú phân công", result.getNote());
        assertNull(result.getId());
        assertNull(result.getVersion());
        assertNull(result.getScheduleStatus());
        assertNull(result.getAssignedAt());
    }
}
