package mss301.providerservice.mapper;

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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProviderViewMapperTests {

    ProviderViewMapper mapper = new ProviderViewMapperImpl();

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

        CaregiverResponse response = mapper.caregiver(profile);

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
                .verificationStatus(VerificationStatus.VERIFIED)
                .version(0L)
                .build();
        cert.setId(certId);

        CertificateResponse response = mapper.certificate(cert);

        assertEquals(certId, response.getId());
        assertEquals(caregiverId, response.getCaregiverId());
        assertEquals("Chứng chỉ sơ cấp cứu", response.getCertificateName());
        assertEquals("Hội Chữ Thập Đỏ", response.getIssuedBy());
        assertEquals("RED-CROSS-12345", response.getCredentialNumber());
        assertEquals(VerificationStatus.VERIFIED, response.getVerificationStatus());
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

        WorkShiftResponse response = mapper.shift(shift, 2L);

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

        ShiftRegistrationResponse response = mapper.registration(reg);

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

        ScheduleResponse response = mapper.schedule(schedule);

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
}
