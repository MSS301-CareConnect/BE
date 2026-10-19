package mss301.providerservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.dto.request.CaregiverCreateRequest;
import mss301.providerservice.dto.request.CaregiverUpdateRequest;
import mss301.providerservice.dto.request.VerificationRequest;
import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.CaregiverResponse;
import mss301.providerservice.dto.response.PageResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.VerificationStatus;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import mss301.providerservice.mapper.ProviderViewMapper;
import mss301.providerservice.repository.CareCenterRepository;
import mss301.providerservice.repository.CaregiverProfileRepository;
import mss301.providerservice.security.ProviderAuthorization;
import mss301.providerservice.service.CaregiverService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CaregiverServiceImpl implements CaregiverService {

    static int MAX_PAGE_SIZE = 100;

    CareCenterRepository centerRepository;
    CaregiverProfileRepository caregiverRepository;
    ProviderAuthorization authorization;
    ProviderViewMapper mapper;

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> create(UUID actorId, UUID centerId, CaregiverCreateRequest request) {
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        if (center.getCenterStatus() != CenterStatus.ACTIVE) {
            throw new AppException(ErrorCode.CENTER_NOT_ACTIVE);
        }
        if (caregiverRepository.existsByUserIdAndDeletedAtIsNull(request.getUserId())) {
            throw new AppException(ErrorCode.CAREGIVER_USER_EXISTED);
        }
        CaregiverProfile caregiver = CaregiverProfile.builder()
                .userId(request.getUserId())
                .center(center)
                .fullName(request.getFullName().trim())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .experienceYears(request.getExperienceYears())
                .bio(normalize(request.getBio()))
                .ratingAverage(BigDecimal.ZERO)
                .ratingCount(0)
                .verificationStatus(VerificationStatus.PENDING)
                .caregiverStatus(CaregiverStatus.INACTIVE)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        return APIResponse.success("Tạo hồ sơ người chăm sóc thành công", mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> listManaged(UUID actorId, UUID centerId,
                                                                    VerificationStatus verification,
                                                                    CaregiverStatus status, int page, int size) {
        validatePage(page, size);
        CareCenter center = centerRepository.findByIdAndDeletedAtIsNull(centerId)
                .orElseThrow(() -> new AppException(ErrorCode.CENTER_NOT_FOUND));
        authorization.requireCenterManagement(center, actorId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CaregiverProfile> result;
        if (verification != null) {
            result = caregiverRepository.findByCenterIdAndVerificationStatusAndDeletedAtIsNull(centerId, verification, pageable);
        } else if (status != null) {
            result = caregiverRepository.findByCenterIdAndCaregiverStatusAndDeletedAtIsNull(centerId, status, pageable);
        } else {
            result = caregiverRepository.findByCenterIdAndDeletedAtIsNull(centerId, pageable);
        }
        return APIResponse.success("Lấy danh sách hồ sơ người chăm sóc thành công",
                PageResponse.of(result, result.stream().map(mapper::caregiver).toList()));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> get(UUID actorId, UUID caregiverId) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCaregiverSelfOrCenterManagement(caregiver, actorId);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", mapper.caregiver(caregiver));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> get(UUID caregiverId) {
        CaregiverProfile caregiver = find(caregiverId);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", mapper.caregiver(caregiver));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> getByUser(UUID userId) {
        CaregiverProfile caregiver = findByUser(userId);
        return APIResponse.success("Lấy hồ sơ người chăm sóc thành công", mapper.caregiver(caregiver));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<CaregiverResponse> myProfile(UUID actorId) {
        return APIResponse.success("Lấy hồ sơ của tôi thành công", mapper.caregiver(findByUser(actorId)));
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<PageResponse<CaregiverResponse>> catalog(UUID centerId, String keyword, int page, int size) {
        validatePage(page, size);
        String normalizedKeyword = normalize(keyword);
        Page<CaregiverProfile> result = caregiverRepository.catalog(centerId, normalizedKeyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "ratingAverage")
                        .and(Sort.by(Sort.Direction.DESC, "experienceYears"))));
        return APIResponse.success("Lấy danh sách caregiver đang hoạt động thành công",
                PageResponse.of(result, result.stream().map(mapper::caregiver).toList()));
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> update(UUID actorId, UUID caregiverId, CaregiverUpdateRequest request) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, request.getVersion());
        caregiver.setFullName(request.getFullName().trim());
        caregiver.setGender(request.getGender());
        caregiver.setDateOfBirth(request.getDateOfBirth());
        caregiver.setExperienceYears(request.getExperienceYears());
        caregiver.setBio(normalize(request.getBio()));
        caregiver.setCaregiverStatus(request.getCaregiverStatus());
        caregiver.setUpdatedBy(actorId);
        return APIResponse.success("Cập nhật hồ sơ người chăm sóc thành công",
                mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional
    public APIResponse<CaregiverResponse> verify(UUID actorId, UUID caregiverId, VerificationRequest request) {
        CaregiverProfile caregiver = find(caregiverId);
        checkVersion(caregiver, request.getVersion());
        if (request.getStatus() == VerificationStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if (request.getStatus() == VerificationStatus.REJECTED && isBlank(request.getReason())) {
            throw new AppException(ErrorCode.VERIFICATION_REASON_REQUIRED);
        }
        caregiver.setVerificationStatus(request.getStatus());
        caregiver.setVerificationReason(normalize(request.getReason()));
        caregiver.setVerifiedBy(actorId);
        caregiver.setVerifiedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiver.setCaregiverStatus(request.getStatus() == VerificationStatus.VERIFIED
                ? CaregiverStatus.ACTIVE : CaregiverStatus.INACTIVE);
        return APIResponse.success("Xác minh hồ sơ người chăm sóc thành công",
                mapper.caregiver(caregiverRepository.save(caregiver)));
    }

    @Override
    @Transactional
    public APIResponse<Void> archive(UUID actorId, UUID caregiverId, Long version) {
        CaregiverProfile caregiver = find(caregiverId);
        authorization.requireCenterManagement(caregiver.getCenter(), actorId);
        checkVersion(caregiver, version);
        caregiver.setCaregiverStatus(CaregiverStatus.ARCHIVED);
        caregiver.setDeletedAt(Instant.now());
        caregiver.setUpdatedBy(actorId);
        caregiverRepository.save(caregiver);
        return APIResponse.success("Lưu trữ hồ sơ người chăm sóc thành công");
    }

    @Override
    public CaregiverProfile findByUser(UUID userId) {
        return caregiverRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    @Override
    public CaregiverProfile find(UUID id) {
        return caregiverRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.CAREGIVER_NOT_FOUND));
    }

    private void checkVersion(CaregiverProfile caregiver, Long version) {
        if (version == null || !version.equals(caregiver.getVersion())) {
            throw new AppException(ErrorCode.CAREGIVER_VERSION_CONFLICT);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGE);
        }
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
