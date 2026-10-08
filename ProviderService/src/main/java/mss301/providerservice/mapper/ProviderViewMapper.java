package mss301.providerservice.mapper;

import mss301.providerservice.dto.response.*;
import mss301.providerservice.entity.*;
import org.springframework.stereotype.Component;

@Component
public class ProviderViewMapper {
    public CaregiverResponse caregiver(CaregiverProfile value) {
        return CaregiverResponse.builder()
                .id(value.getId()).userId(value.getUserId())
                .centerId(value.getCenter().getId()).centerName(value.getCenter().getCenterName())
                .fullName(value.getFullName()).gender(value.getGender()).dateOfBirth(value.getDateOfBirth())
                .experienceYears(value.getExperienceYears()).bio(value.getBio())
                .ratingAverage(value.getRatingAverage()).ratingCount(value.getRatingCount())
                .verificationStatus(value.getVerificationStatus()).caregiverStatus(value.getCaregiverStatus())
                .verificationReason(value.getVerificationReason()).verifiedBy(value.getVerifiedBy())
                .verifiedAt(value.getVerifiedAt()).createdBy(value.getCreatedBy()).updatedBy(value.getUpdatedBy())
                .version(value.getVersion()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt())
                .build();
    }

    public CertificateResponse certificate(Certificate value) {
        return CertificateResponse.builder()
                .id(value.getId()).caregiverId(value.getCaregiver().getId())
                .certificateName(value.getCertificateName()).issuedBy(value.getIssuedBy())
                .credentialNumber(value.getCredentialNumber()).issuedDate(value.getIssuedDate())
                .expiryDate(value.getExpiryDate()).verificationStatus(value.getVerificationStatus())
                .verificationReason(value.getVerificationReason()).verifiedBy(value.getVerifiedBy())
                .verifiedAt(value.getVerifiedAt()).version(value.getVersion())
                .createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    public WorkShiftResponse shift(WorkShift value, long approvedCount) {
        return WorkShiftResponse.builder()
                .id(value.getId()).centerId(value.getCenter().getId()).centerName(value.getCenter().getCenterName())
                .workDate(value.getWorkDate()).startTime(value.getStartTime()).endTime(value.getEndTime())
                .shiftType(value.getShiftType()).shiftStatus(value.getShiftStatus()).capacity(value.getCapacity())
                .approvedCount(approvedCount).registrationDeadline(value.getRegistrationDeadline()).note(value.getNote())
                .version(value.getVersion()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    public ShiftRegistrationResponse registration(ShiftRegistration value) {
        return ShiftRegistrationResponse.builder()
                .id(value.getId()).workShiftId(value.getWorkShift().getId())
                .caregiverId(value.getCaregiver().getId()).caregiverName(value.getCaregiver().getFullName())
                .registrationStatus(value.getRegistrationStatus()).caregiverNote(value.getCaregiverNote())
                .reviewedBy(value.getReviewedBy()).reviewedAt(value.getReviewedAt())
                .rejectionReason(value.getRejectionReason()).version(value.getVersion())
                .createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    public ScheduleResponse schedule(CaregiverSchedule value) {
        WorkShift shift = value.getWorkShift();
        return ScheduleResponse.builder()
                .id(value.getId()).registrationId(value.getRegistration().getId())
                .caregiverId(value.getCaregiver().getId()).caregiverName(value.getCaregiver().getFullName())
                .workShiftId(shift.getId()).centerId(shift.getCenter().getId())
                .workDate(shift.getWorkDate()).startTime(shift.getStartTime()).endTime(shift.getEndTime())
                .scheduleStatus(value.getScheduleStatus()).assignedBy(value.getAssignedBy())
                .assignedAt(value.getAssignedAt()).note(value.getNote())
                .cancellationReason(value.getCancellationReason()).cancelledAt(value.getCancelledAt())
                .version(value.getVersion()).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }
}
